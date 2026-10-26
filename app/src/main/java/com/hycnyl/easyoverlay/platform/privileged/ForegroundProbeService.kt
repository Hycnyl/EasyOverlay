package com.hycnyl.easyoverlay.platform.privileged

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Process
import androidx.annotation.Keep
import org.json.JSONObject

/** Loaded by Shizuku, never exported as an ordinary Android service. */
@Keep
class ForegroundProbeService : IPrivilegedProbe.Stub {
    private val context: Context?
    private val clientUid: Int
    private val shell = BoundedShell()
    private var focusedApiUsable = true
    private val ownPackage = OptimizationSnapshot.OWN_PACKAGE

    @Keep constructor() { context = null; clientUid = -1 }
    @Keep constructor(context: Context) { this.context = context; clientUid = context.applicationInfo.uid }

    private fun enforceClient() {
        val caller = Binder.getCallingUid()
        check(Process.myUid() == 0 || Process.myUid() == 2000) { "NOT_A_SHIZUKU_PROCESS" }
        check(caller == clientUid || caller == Process.myUid()) { "UNEXPECTED_CALLER" }
    }

    override fun foregroundSnapshot(): String {
        enforceClient()
        val token = Binder.clearCallingIdentity()
        return try {
            val component = readFocusedTask() ?: readTasks()
            if (component != null) snapshot(component, "TASKS") else snapshot(readDumpsys(), "DUMPSYS")
        } finally { Binder.restoreCallingIdentity(token) }
    }

    private fun readFocusedTask(): ComponentName? {
        if (!focusedApiUsable) return null
        return try {
            val type = Class.forName("android.app.ActivityTaskManager")
            val service = type.getMethod("getService").invoke(null)
            val task = service.javaClass.getMethod("getFocusedRootTaskInfo").invoke(service) ?: return null
            if (fieldInt(task, "displayId") != 0) return null
            task.javaClass.getField("topActivity").get(task) as? ComponentName
        } catch (_: Exception) { focusedApiUsable = false; null }
    }

    private fun readTasks(): ComponentName? = runCatching {
        val type = Class.forName("android.app.ActivityTaskManager")
        val instance = type.getMethod("getInstance").invoke(null)
        val tasks = try {
            type.getMethod("getTasks", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(instance, 16, false, false, 0)
        } catch (_: NoSuchMethodException) { type.getMethod("getTasks", Int::class.javaPrimitiveType).invoke(instance, 16) }
        val list = (tasks as? List<*>)?.filterNotNull().orEmpty().filter { fieldInt(it, "displayId") == 0 }
        val selected = list.firstOrNull { fieldBoolean(it, "isFocused") == true }
            ?: list.firstOrNull { fieldBoolean(it, "isVisible") == true }
            ?: list.firstOrNull()
        selected?.javaClass?.getField("topActivity")?.get(selected) as? ComponentName
    }.getOrNull()

    private fun fieldInt(value: Any, name: String): Int = runCatching { value.javaClass.getField(name).getInt(value) }.getOrDefault(0)
    private fun fieldBoolean(value: Any, name: String): Boolean? = runCatching { value.javaClass.getField(name).getBoolean(value) }.getOrNull()

    private fun readDumpsys(): ComponentName? = runCatching {
        val result = shell.run("/system/bin/dumpsys", "activity", "activities", timeoutMs = 700, limit = 512 * 1024)
        check(result.exitCode == 0)
        PrivilegedSnapshotParsers.defaultDisplayComponent(result.output)?.let(ComponentName::unflattenFromString)
    }.getOrNull()

    private fun snapshot(component: ComponentName?, method: String): String = JSONObject()
        .put("packageName", component?.packageName ?: JSONObject.NULL).put("displayId", 0)
        .put("observedAt", System.currentTimeMillis()).put("method", method).put("uid", Process.myUid())
        .put("problem", if (component == null) "NO_FOREGROUND_TASK" else JSONObject.NULL).toString()

    override fun optimizationSnapshot(): String = identity { ownStatus().toJson().toString() }
    override fun applySelfOptimization(originalSnapshot: String): String = identity { changeSelf(OptimizationSnapshot.fromJson(JSONObject(originalSnapshot)), false) }
    override fun restoreSelfOptimization(originalSnapshot: String): String = identity { changeSelf(OptimizationSnapshot.fromJson(JSONObject(originalSnapshot)), true) }

    private fun <T> identity(action: () -> T): T {
        enforceClient(); val token = Binder.clearCallingIdentity()
        return try { action() } finally { Binder.restoreCallingIdentity(token) }
    }

    private fun ownUid(): Int {
        val actual = context?.packageManager?.getApplicationInfo(ownPackage, 0)?.uid ?: clientUid
        check(actual >= 10000 && actual == clientUid) { "INVALID_SELF_UID" }
        val shared = context?.packageManager?.getPackagesForUid(actual).orEmpty()
        check(shared.size == 1 && shared.single() == ownPackage) { "SHARED_UID_UNSUPPORTED" }
        return actual
    }

    private fun ownStatus(): OptimizationSnapshot {
        val uid = ownUid(); val user = (uid / 100000).toString()
        val notificationGranted = context?.packageManager?.checkPermission("android.permission.POST_NOTIFICATIONS", ownPackage) == PackageManager.PERMISSION_GRANTED
        val ops = shell.run("/system/bin/cmd", "appops", "get", "--user", user, ownPackage, "GET_USAGE_STATS")
        check(ops.exitCode == 0) { "APP_OPS_READ_FAILED" }
        val modes = PrivilegedSnapshotParsers.usageModes(ops.output)
        val whitelist = shell.run("/system/bin/cmd", "deviceidle", "whitelist")
        check(whitelist.exitCode == 0) { "BATTERY_READ_FAILED" }
        val (userWhite, systemWhite) = PrivilegedSnapshotParsers.batteryWhitelist(whitelist.output, ownPackage)
        return OptimizationSnapshot(uid, notificationGranted, modes.packageMode, modes.uidMode, userWhite, systemWhite)
    }

    private fun changeSelf(original: OptimizationSnapshot, restore: Boolean): String {
        check(original.uid == ownUid()) { "UID_CHANGED" }
        val before = ownStatus()
        val results = org.json.JSONArray()
        fun item(name: String, action: () -> String) {
            try { results.put(JSONObject().put("item", name).put("status", action()).put("success", true)) }
            catch (_: Exception) { results.put(JSONObject().put("item", name).put("status", "FAILED").put("success", false)) }
        }
        val user = (original.uid / 100000).toString()
        fun command(vararg args: String) { val result = shell.run(*args); check(result.exitCode == 0) { "COMMAND_FAILED" } }
        item("notification") {
            when {
                original.notificationGranted -> "UNCHANGED_ORIGINALLY_GRANTED"
                restore && !before.notificationGranted -> "ALREADY_RESTORED"
                restore -> { command("/system/bin/pm", "revoke", "--user", user, ownPackage, "android.permission.POST_NOTIFICATIONS"); check(!ownStatus().notificationGranted); "RESTORED" }
                before.notificationGranted -> "ALREADY_ENABLED"
                else -> { command("/system/bin/pm", "grant", "--user", user, ownPackage, "android.permission.POST_NOTIFICATIONS"); check(ownStatus().notificationGranted); "APPLIED" }
            }
        }
        item("usage") {
            val originallyAllowed = original.usageUidMode == "allow" || (original.usageUidMode == "default" && original.usagePackageMode == "allow")
            if (originallyAllowed) "UNCHANGED_ORIGINALLY_ALLOWED"
            else if (restore) {
                if (before.usagePackageMode !in setOf("allow", original.usagePackageMode) || before.usageUidMode !in setOf("allow", original.usageUidMode)) error("MODIFIED_EXTERNALLY")
                command("/system/bin/cmd", "appops", "set", "--user", user, ownPackage, "GET_USAGE_STATS", original.usagePackageMode)
                if (original.usageUidMode != "default") command("/system/bin/cmd", "appops", "set", "--user", user, "--uid", ownPackage, "GET_USAGE_STATS", original.usageUidMode)
                val status = ownStatus(); check(status.usagePackageMode == original.usagePackageMode && status.usageUidMode == original.usageUidMode)
                "RESTORED"
            } else {
                command("/system/bin/cmd", "appops", "set", "--user", user, ownPackage, "GET_USAGE_STATS", "allow")
                if (before.usageUidMode !in setOf("allow", "default")) command("/system/bin/cmd", "appops", "set", "--user", user, "--uid", ownPackage, "GET_USAGE_STATS", "allow")
                val status = ownStatus(); check(status.usageUidMode == "allow" || (status.usageUidMode == "default" && status.usagePackageMode == "allow"))
                "APPLIED"
            }
        }
        item("battery") {
            when {
                original.batteryWhitelisted -> "UNCHANGED_ORIGINALLY_ALLOWED"
                restore && !before.batteryUserWhitelisted -> "ALREADY_RESTORED"
                restore -> { command("/system/bin/cmd", "deviceidle", "whitelist", "-$ownPackage"); check(!ownStatus().batteryUserWhitelisted); "RESTORED" }
                before.batteryWhitelisted -> "ALREADY_ENABLED"
                else -> { command("/system/bin/cmd", "deviceidle", "whitelist", "+$ownPackage"); check(ownStatus().batteryUserWhitelisted); "APPLIED" }
            }
        }
        return JSONObject().put("restored", restore).put("items", results).toString()
    }

    override fun destroy() {
        enforceClient(); shell.close(); kotlin.system.exitProcess(0)
    }
}
