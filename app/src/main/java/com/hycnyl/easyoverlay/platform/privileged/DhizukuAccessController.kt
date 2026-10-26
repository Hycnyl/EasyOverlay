package com.hycnyl.easyoverlay.platform.privileged

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import com.rosan.dhizuku.api.DhizukuUserServiceArgs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class DhizukuAccessState(
    val installed: Boolean = false,
    val available: Boolean = false,
    val granted: Boolean = false,
    val connected: Boolean = false,
    val ownerPackage: String? = null,
    val deviceOwner: Boolean = false,
    val profileOwner: Boolean = false,
    val serverVersion: Int? = null,
    val problem: String? = null
) { val supportsTaskMonitoring: Boolean get() = false }

data class DhizukuNotificationPreview(val originalGrantState: Int, val notificationGranted: Boolean, val ownerPackage: String,
    val uid: Int, val restoreAvailable: Boolean = false) {
    val hasChanges get() = !notificationGranted
    internal fun toJson() = JSONObject().put("schema", 1).put("packageName", OptimizationSnapshot.OWN_PACKAGE).put("uid", uid)
        .put("policyGrantState", originalGrantState).put("notificationGranted", notificationGranted).put("ownerPackage", ownerPackage)
}

/** Device-owner integration is scoped to a visible, explicit own-notification operation. */
class DhizukuAccessController(private val context: Context, private val scope: CoroutineScope) {
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(DhizukuAccessState())
    val state = _state.asStateFlow()
    private val _canRestore = MutableStateFlow(false)
    val canRestore = _canRestore.asStateFlow()
    private val operations = Mutex()
    private val gate = BinderCallGate()
    private val store = SelfOptimizationStore(context, "dhizuku-self-notification-v1.json")
    private var proxy: IDeviceOwnerHelper? = null
    private var connection: ServiceConnection? = null
    private var connectionToken = 0L
    private var closed = false
    private val args = DhizukuUserServiceArgs(ComponentName(context.packageName, DhizukuNotificationService::class.java.name))

    init { connect(); scope.launch { _canRestore.value = runCatching { store.read() != null }.getOrDefault(false) } }

    fun connect() {
        if (closed) return
        scope.launch(Dispatchers.IO) {
            val installed = runCatching { context.packageManager.getPackageInfo("com.rosan.dhizuku", 0); true }.getOrDefault(false)
            val available = runCatching { Dhizuku.init(context) }.getOrDefault(false)
            if (!available) { _state.value = DhizukuAccessState(installed = installed, problem = if (installed) "DHIZUKU_NOT_ACTIVATED" else "DHIZUKU_NOT_INSTALLED"); return@launch }
            val granted = runCatching { Dhizuku.isPermissionGranted() }.getOrDefault(false)
            val owner = runCatching { Dhizuku.getOwnerPackageName() }.getOrNull()
            val manager = context.getSystemService(DevicePolicyManager::class.java)
            _state.value = DhizukuAccessState(installed || available, available, granted, proxy != null, owner,
                owner?.let { runCatching { manager.isDeviceOwnerApp(it) }.getOrDefault(false) } ?: false,
                owner?.let { runCatching { manager.isProfileOwnerApp(it) }.getOrDefault(false) } ?: false,
                runCatching { Dhizuku.getVersionCode() }.getOrNull(), if (granted) null else "DHIZUKU_PERMISSION_REQUIRED")
        }
    }

    fun requestPermission(activity: Activity) {
        if (closed || activity.isDestroyed || activity.isFinishing) return
        main.post {
            if (!runCatching { Dhizuku.init(context) }.getOrDefault(false)) { connect(); return@post }
            runCatching { Dhizuku.requestPermission(object : DhizukuRequestPermissionListener() {
                override fun onRequestPermission(grantResult: Int) { connect() }
            }) }.onFailure { _state.value = _state.value.copy(problem = "DHIZUKU_PERMISSION_REQUEST_FAILED") }
        }
    }

    private suspend fun helper(): IDeviceOwnerHelper {
        withContext(Dispatchers.Main.immediate) {
            check(!closed && runCatching { Dhizuku.init(context) && Dhizuku.isPermissionGranted() }.getOrDefault(false)) { "DHIZUKU_PERMISSION_REQUIRED" }
            if (proxy == null && connection == null) {
                val token=++connectionToken
                val listener = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) { main.post {
                        if (closed || token != connectionToken || binder == null) return@post
                        proxy = IDeviceOwnerHelper.Stub.asInterface(binder)
                        _state.value = _state.value.copy(connected = true, problem = null)
                        runCatching { binder.linkToDeath({ main.post { if(token==connectionToken){releaseHelper();_state.value = _state.value.copy(problem = "DHIZUKU_BINDER_DIED");connect()} } }, 0) }
                    } }
                    override fun onServiceDisconnected(name: ComponentName?) { main.post { if(token==connectionToken){releaseHelper();_state.value = _state.value.copy(problem = "DHIZUKU_BINDER_DIED");connect()} } }
                }
                connection = listener
                check(Dhizuku.bindUserService(args, listener)) { "DHIZUKU_BIND_FAILED" }
            }
        }
        return withTimeout(25000) { while (proxy == null) delay(100); proxy!! }
    }

    private fun releaseHelper() {
        connectionToken++
        val old = connection; connection = null; proxy = null
        if (old != null) runCatching { Dhizuku.unbindUserService(old) }
        _state.value = _state.value.copy(connected = false)
    }
    private fun decodePreview(raw: String, restore: Boolean): DhizukuNotificationPreview {
        val value = JSONObject(raw)
        require(value.getString("packageName") == OptimizationSnapshot.OWN_PACKAGE && value.getInt("uid") == context.applicationInfo.uid)
        val grant = value.getInt("policyGrantState"); require(grant in 0..2)
        return DhizukuNotificationPreview(grant, value.getBoolean("notificationGranted"), value.getString("ownerPackage"), value.getInt("uid"), restore)
    }

    suspend fun previewNotificationPermission(): DhizukuNotificationPreview = operations.withLock {
        try { val service = helper(); decodePreview(gate.call(8000) { service.notificationSnapshot() }, store.read() != null) }
        finally { withContext(Dispatchers.Main.immediate) { releaseHelper() } }
    }

    suspend fun grantOwnNotificationPermission(preview: DhizukuNotificationPreview): OptimizationResult = operations.withLock {
        try {
            check(store.read() == null) { "RESTORE_PREVIOUS_OPTIMIZATION_FIRST" }
            val service = helper()
            val current = decodePreview(gate.call(8000) { service.notificationSnapshot() }, false)
            check(current.copy(restoreAvailable = preview.restoreAvailable) == preview) { "OPTIMIZATION_STATE_CHANGED_REVIEW_AGAIN" }
            if (current.notificationGranted) return@withLock OptimizationResult(listOf(OptimizationItemResult("notification", "UNCHANGED_ORIGINALLY_GRANTED", true)))
            store.write(preview.toJson()); _canRestore.value = true
            val after = decodePreview(gate.call(10000) { service.setOwnNotificationGrant(DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED) }, true)
            OptimizationResult(listOf(OptimizationItemResult("notification", if (after.notificationGranted) "APPLIED" else "FAILED", after.notificationGranted)))
        } finally { withContext(Dispatchers.Main.immediate) { releaseHelper() } }
    }

    /** Convenience still requires the caller to obtain/confirm the preview first. */
    suspend fun restoreOwnNotificationPermission(): OptimizationResult = operations.withLock {
        try {
            val raw = store.read() ?: error("NO_RESTORE_POINT")
            val original = decodePreview(raw.toString(), true)
            val service = helper()
            val current = decodePreview(gate.call(8000) { service.notificationSnapshot() }, true)
            check(current.ownerPackage == original.ownerPackage) { "DEVICE_OWNER_CHANGED" }
            if (original.originalGrantState == DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT) {
                // DEFAULT only removes policy-fixed; first restore the original actual grant, then release policy control.
                gate.call(10000) { service.setOwnNotificationGrant(if (original.notificationGranted) DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED else DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED) }
            }
            val after = decodePreview(gate.call(10000) { service.setOwnNotificationGrant(original.originalGrantState) }, true)
            val restored = after.originalGrantState == original.originalGrantState && after.notificationGranted == original.notificationGranted
            if (restored) { store.clear(); _canRestore.value = false }
            OptimizationResult(listOf(OptimizationItemResult("notification", if (restored) "RESTORED" else "FAILED", restored)), true)
        } finally { withContext(Dispatchers.Main.immediate) { releaseHelper() } }
    }

    fun close() { closed = true; main.post { releaseHelper(); gate.close() } }
}
