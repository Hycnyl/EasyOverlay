package com.hycnyl.easyoverlay.platform.privileged

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Process
import androidx.annotation.Keep
import org.json.JSONObject

/** Runs as the Dhizuku Device/Profile Owner app UID, not shell/root. No task-monitor API is exposed. */
@Keep
class DhizukuNotificationService : IDeviceOwnerHelper.Stub {
    private val context: Context?
    private val clientUid: Int
    private val hostUid = Process.myUid()
    private val ownPackage = OptimizationSnapshot.OWN_PACKAGE

    @Keep constructor() { context = null; clientUid = -1 }
    @Keep constructor(context: Context) {
        this.context = context
        clientUid = context.packageManager.getApplicationInfo(ownPackage, 0).uid
    }

    private fun enforceClient() { check(clientUid >= 10000 && Binder.getCallingUid() == clientUid) { "UNEXPECTED_CALLER" } }
    private fun manager(): Pair<DevicePolicyManager, ComponentName> {
        val host = context ?: error("OWNER_CONTEXT_UNAVAILABLE")
        val candidate = host.getSystemService(DevicePolicyManager::class.java)
        val admin = candidate.activeAdmins.orEmpty().firstOrNull {
            candidate.isDeviceOwnerApp(it.packageName) || candidate.isProfileOwnerApp(it.packageName)
        } ?: error("DEVICE_OWNER_UNAVAILABLE")
        check(host.packageManager.getApplicationInfo(admin.packageName, 0).uid == hostUid) { "NOT_DEVICE_OWNER_PROCESS" }
        val ownerContext = host.createPackageContext(admin.packageName, 0)
        return ownerContext.getSystemService(DevicePolicyManager::class.java) to admin
    }
    private fun status(): JSONObject {
        val (manager, admin) = manager()
        val granted = context!!.packageManager.checkPermission(NOTIFICATION, ownPackage) == PackageManager.PERMISSION_GRANTED
        return JSONObject().put("schema", 1).put("packageName", ownPackage).put("uid", clientUid)
            .put("policyGrantState", manager.getPermissionGrantState(admin, ownPackage, NOTIFICATION))
            .put("notificationGranted", granted).put("ownerPackage", admin.packageName)
            .put("deviceOwner", manager.isDeviceOwnerApp(admin.packageName)).put("profileOwner", manager.isProfileOwnerApp(admin.packageName))
    }
    override fun onStart() { check(Binder.getCallingUid() == hostUid || Binder.getCallingUid() == clientUid) }
    override fun onStop() { check(Binder.getCallingUid() == hostUid || Binder.getCallingUid() == clientUid) }
    override fun notificationSnapshot(): String = identity { status().toString() }
    override fun setOwnNotificationGrant(grantState: Int): String = identity {
        require(grantState in 0..2)
        val (manager, admin) = manager()
        check(manager.setPermissionGrantState(admin, ownPackage, NOTIFICATION, grantState)) { "DPM_POLICY_REJECTED" }
        status().toString()
    }
    private fun <T> identity(action: () -> T): T {
        enforceClient(); val token = Binder.clearCallingIdentity()
        return try { action() } finally { Binder.restoreCallingIdentity(token) }
    }
    private companion object { const val NOTIFICATION = "android.permission.POST_NOTIFICATIONS" }
}
