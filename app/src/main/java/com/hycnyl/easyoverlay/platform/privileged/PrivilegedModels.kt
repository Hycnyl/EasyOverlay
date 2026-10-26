package com.hycnyl.easyoverlay.platform.privileged

import org.json.JSONArray
import org.json.JSONObject

/** Capability names describe probed access; none implies that Android cannot stop the app. */
enum class PrivilegedCapability { TASK_SNAPSHOT, DUMP_SNAPSHOT }
enum class PrivilegedConnection { DISCONNECTED, SERVER_READY, BINDING, CONNECTED }
enum class PrivilegedProblem {
    NOT_INSTALLED, SERVER_NOT_RUNNING, PERMISSION_REQUIRED, UNSUPPORTED_VERSION,
    BINDER_DIED, BIND_TIMEOUT, PROBE_TIMEOUT, PROBE_FAILED, NO_FOREGROUND_TASK, CAPABILITY_MISSING, SHARED_UID_UNSUPPORTED
}

data class PrivilegedAccessState(
    val available: Boolean = false,
    val granted: Boolean = false,
    val binderAlive: Boolean = false,
    val uid: Int? = null,
    val capabilities: Set<PrivilegedCapability> = emptySet(),
    val connection: PrivilegedConnection = PrivilegedConnection.DISCONNECTED,
    val problem: PrivilegedProblem? = null,
    val installed: Boolean = false
)

data class PrivilegedForegroundSnapshot(
    val packageName: String?,
    val displayId: Int = 0,
    val observedAt: Long = System.currentTimeMillis(),
    val method: String = "NONE",
    val problem: PrivilegedProblem? = null
)

data class OptimizationSnapshot(
    val uid: Int,
    val notificationGranted: Boolean,
    val usagePackageMode: String,
    val usageUidMode: String,
    val batteryUserWhitelisted: Boolean,
    val batterySystemWhitelisted: Boolean
) {
    val batteryWhitelisted get() = batteryUserWhitelisted || batterySystemWhitelisted
    fun toJson(): JSONObject = JSONObject().put("schema", 1).put("packageName", OWN_PACKAGE)
        .put("uid", uid).put("notificationGranted", notificationGranted)
        .put("usagePackageMode", usagePackageMode).put("usageUidMode", usageUidMode)
        .put("batteryUserWhitelisted", batteryUserWhitelisted).put("batterySystemWhitelisted", batterySystemWhitelisted)

    companion object {
        const val OWN_PACKAGE = "com.hycnyl.easyoverlay"
        val MODES = setOf("allow", "ignore", "deny", "default", "foreground")
        fun fromJson(value: JSONObject): OptimizationSnapshot {
            require(value.getInt("schema") == 1 && value.getString("packageName") == OWN_PACKAGE)
            val packageMode = value.getString("usagePackageMode")
            val uidMode = value.getString("usageUidMode")
            require(packageMode in MODES && uidMode in MODES)
            return OptimizationSnapshot(value.getInt("uid"), value.getBoolean("notificationGranted"), packageMode, uidMode,
                value.getBoolean("batteryUserWhitelisted"), value.getBoolean("batterySystemWhitelisted"))
        }
    }
}

data class OptimizationPreview(val original: OptimizationSnapshot, val restoreAvailable: Boolean = false) {
    val changeNotification get() = !original.notificationGranted
    val changeUsage get() = !(original.usageUidMode == "allow" || (original.usageUidMode == "default" && original.usagePackageMode == "allow"))
    val changeBattery get() = !original.batteryWhitelisted
    val hasChanges get() = changeNotification || changeUsage || changeBattery
}

data class OptimizationItemResult(val item: String, val status: String, val success: Boolean)
data class OptimizationResult(val items: List<OptimizationItemResult>, val restored: Boolean = false) {
    val successful get() = items.all { it.success }
    companion object {
        fun fromJson(value: String): OptimizationResult {
            val root = JSONObject(value)
            val array = root.getJSONArray("items")
            require(array.length() in 1..3)
            val items = (0 until array.length()).map { index -> array.getJSONObject(index).let {
                OptimizationItemResult(it.getString("item"), it.getString("status"), it.getBoolean("success"))
            } }
            require(items.map { it.item }.distinct().size == items.size && items.all { it.item in setOf("notification", "usage", "battery") })
            return OptimizationResult(items, root.optBoolean("restored", false))
        }
    }
}
