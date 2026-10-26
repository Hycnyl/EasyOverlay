package com.hycnyl.easyoverlay.platform.privileged

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.hycnyl.easyoverlay.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeoutException

/** Optional Shizuku bridge. Authorization and the visible automatic session remain user-controlled. */
class PrivilegedAccessController(private val context: Context, private val scope: CoroutineScope) {
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(PrivilegedAccessState())
    val state = _state.asStateFlow()
    private val _canRestore = MutableStateFlow(false)
    val canRestore = _canRestore.asStateFlow()
    private val gate = BinderCallGate()
    private val operations = Mutex()
    private val store = SelfOptimizationStore(context, "shizuku-self-optimization-v1.json")
    private var initialized = false
    private var closed = false
    @Volatile private var proxy: IPrivilegedProbe? = null
    private var connection: ServiceConnection? = null
    private var connectionToken = 0L
    private var bindTimeout: Job? = null
    private var retryAfter = 0L
    private var loop: Job? = null
    @Volatile private var leases = 0
    @Volatile private var optimizing = false
    @Volatile private var monitorLeaseActive = false
    private val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, ForegroundProbeService::class.java.name))
        .processNameSuffix("foreground_probe").tag("easyoverlay-foreground-v1").version(BuildConfig.VERSION_CODE).daemon(false)

    private val binderReceived = Shizuku.OnBinderReceivedListener { main.post { refreshState(); if (loop?.isActive == true) ensureBound() } }
    private val binderDead = Shizuku.OnBinderDeadListener { main.post { disconnect(false); refreshState(PrivilegedProblem.BINDER_DIED) } }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { request, _ ->
        if (request == REQUEST_CODE) main.post { refreshState(); if (loop?.isActive == true) ensureBound() }
    }

    init { connect(); scope.launch { _canRestore.value = runCatching { store.read() != null }.getOrDefault(false) } }

    fun connect() {
        main.post {
            if (closed) return@post
            if (!initialized) {
                initialized = true
                Shizuku.addBinderReceivedListenerSticky(binderReceived)
                Shizuku.addBinderDeadListener(binderDead)
                Shizuku.addRequestPermissionResultListener(permissionResult)
            }
            refreshState()
        }
    }

    fun requestPermission(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        main.post {
            refreshState()
            if (!_state.value.binderAlive || closed) return@post
            runCatching { Shizuku.requestPermission(REQUEST_CODE) }.onFailure { refreshState(PrivilegedProblem.PERMISSION_REQUIRED) }
        }
    }

    private fun refreshState(problem: PrivilegedProblem? = null) {
        val installed = runCatching { context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0); true }.getOrDefault(false)
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val supported = alive && runCatching { !Shizuku.isPreV11() && Shizuku.getVersion() >= 13 }.getOrDefault(false)
        val granted = supported && runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        val uid = if (granted) runCatching { Shizuku.getUid() }.getOrNull() else null
        val capabilities = buildSet {
            if (granted && runCatching { Shizuku.checkRemotePermission("android.permission.REAL_GET_TASKS") == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)) add(PrivilegedCapability.TASK_SNAPSHOT)
            if (granted && runCatching { Shizuku.checkRemotePermission("android.permission.DUMP") == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)) add(PrivilegedCapability.DUMP_SNAPSHOT)
        }
        val current = _state.value
        _state.value = PrivilegedAccessState(granted && alive && capabilities.isNotEmpty(), granted, alive, uid, capabilities,
            if (proxy != null) PrivilegedConnection.CONNECTED else if (connection != null) PrivilegedConnection.BINDING else if (granted) PrivilegedConnection.SERVER_READY else PrivilegedConnection.DISCONNECTED,
            problem ?: when { !installed && !alive -> PrivilegedProblem.NOT_INSTALLED; !alive -> PrivilegedProblem.SERVER_NOT_RUNNING; !supported -> PrivilegedProblem.UNSUPPORTED_VERSION; !granted -> PrivilegedProblem.PERMISSION_REQUIRED; capabilities.isEmpty() -> PrivilegedProblem.CAPABILITY_MISSING; else -> current.problem?.takeIf { it == PrivilegedProblem.PROBE_TIMEOUT && proxy == null } }, installed)
    }

    private fun ensureBound() {
        if (closed || proxy != null || connection != null || System.currentTimeMillis() < retryAfter) return
        refreshState()
        if (!_state.value.granted) return
        val token = ++connectionToken
        val listener = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                main.post {
                    if (closed || token != connectionToken || binder == null) return@post
                    proxy = IPrivilegedProbe.Stub.asInterface(binder)
                    bindTimeout?.cancel(); bindTimeout = null
                    runCatching { binder.linkToDeath({ main.post { if (token == connectionToken) { disconnect(false); retryAfter = System.currentTimeMillis() + 1500; refreshState(PrivilegedProblem.BINDER_DIED) } } }, 0) }
                    refreshState()
                }
            }
            override fun onServiceDisconnected(name: ComponentName?) { main.post { if (token == connectionToken) { disconnect(false); retryAfter = System.currentTimeMillis() + 1500; refreshState(PrivilegedProblem.BINDER_DIED) } } }
        }
        connection = listener
        _state.value = _state.value.copy(connection = PrivilegedConnection.BINDING, problem = null)
        try { Shizuku.bindUserService(args, listener) }
        catch (_: Exception) { disconnect(false); retryAfter = System.currentTimeMillis() + 3000; refreshState(PrivilegedProblem.PROBE_FAILED); return }
        bindTimeout = scope.launch {
            delay(20000)
            if (proxy == null && token == connectionToken) { disconnect(true); retryAfter = System.currentTimeMillis() + 3000; refreshState(PrivilegedProblem.BIND_TIMEOUT) }
        }
    }

    /** One authoritative snapshot for the root monitor's own polling/reconciliation loop. */
    suspend fun foregroundSnapshot(): PrivilegedForegroundSnapshot {
        monitorLeaseActive = true
        leases++
        return try {
            if (optimizing) return PrivilegedForegroundSnapshot(null, problem = PrivilegedProblem.PROBE_FAILED)
            val service = serviceForOperation()
            val raw = gate.call(1400) { service.foregroundSnapshot() }
            val root = JSONObject(raw)
            val pkg = root.optString("packageName").takeUnless { it.isBlank() || it == "null" }
            PrivilegedForegroundSnapshot(pkg, root.optInt("displayId", 0), root.optLong("observedAt", System.currentTimeMillis()), root.optString("method", "TASKS"),
                if (pkg == null) PrivilegedProblem.NO_FOREGROUND_TASK else null)
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            val problem = if (error is TimeoutException || error.cause is TimeoutException) PrivilegedProblem.PROBE_TIMEOUT else _state.value.problem ?: PrivilegedProblem.PROBE_FAILED
            withContext(Dispatchers.Main.immediate) { disconnect(true); retryAfter = System.currentTimeMillis() + 3000; refreshState(problem) }
            PrivilegedForegroundSnapshot(null, problem = problem)
        } finally {
            leases--
            if (!monitorLeaseActive && loop == null && leases == 0) withContext(Dispatchers.Main.immediate) { disconnect(true) }
        }
    }

    fun startForegroundObservation(callback: (PrivilegedForegroundSnapshot) -> Unit) {
        stopForegroundObservation()
        monitorLeaseActive = true
        loop = scope.launch(Dispatchers.Main.immediate) {
            while (isActive && !closed) { if (!optimizing) callback(foregroundSnapshot()); delay(400) }
        }
    }

    fun stopForegroundObservation() {
        monitorLeaseActive = false
        loop?.cancel(); loop = null
        main.post { if (!monitorLeaseActive && loop == null && leases == 0) disconnect(true) }
    }

    private suspend fun serviceForOperation(): IPrivilegedProbe {
        return withTimeout(25000) {
            while (proxy == null) {
                withContext(Dispatchers.Main.immediate) { ensureBound() }
                check(_state.value.granted) { "SHIZUKU_PERMISSION_REQUIRED" }
                delay(100)
            }
            proxy!!
        }
    }

    suspend fun previewOptimization(): OptimizationPreview = operations.withLock {
        optimizing = true; leases++
        try {
            val service = serviceForOperation()
            val original = OptimizationSnapshot.fromJson(JSONObject(gate.call(10000) { service.optimizationSnapshot() }))
            OptimizationPreview(original, store.read() != null)
        } finally { optimizing = false; leases--; withContext(Dispatchers.Main.immediate) { if (!monitorLeaseActive && loop == null && leases == 0) disconnect(true) } }
    }

    suspend fun applyOptimization(preview: OptimizationPreview): OptimizationResult = operations.withLock {
        optimizing = true; leases++
        try {
            check(store.read() == null) { "RESTORE_PREVIOUS_OPTIMIZATION_FIRST" }
            require(preview.original.uid == context.applicationInfo.uid) { "UID_CHANGED" }
            val service = serviceForOperation()
            val fresh = OptimizationSnapshot.fromJson(JSONObject(gate.call(10000) { service.optimizationSnapshot() }))
            check(fresh == preview.original) { "OPTIMIZATION_STATE_CHANGED_REVIEW_AGAIN" }
            store.write(preview.original.toJson()); _canRestore.value = true
            OptimizationResult.fromJson(gate.call(25000) { service.applySelfOptimization(preview.original.toJson().toString()) })
        } finally { optimizing = false; leases--; withContext(Dispatchers.Main.immediate) { if (!monitorLeaseActive && loop == null && leases == 0) disconnect(true) } }
    }

    suspend fun restoreOptimization(): OptimizationResult = operations.withLock {
        optimizing = true; leases++
        try {
            val original = store.read() ?: error("NO_RESTORE_POINT")
            val service = serviceForOperation()
            val result = OptimizationResult.fromJson(gate.call(25000) { service.restoreSelfOptimization(original.toString()) })
            if (result.successful) { store.clear(); _canRestore.value = false }
            result
        } finally { optimizing = false; leases--; withContext(Dispatchers.Main.immediate) { if (!monitorLeaseActive && loop == null && leases == 0) disconnect(true) } }
    }

    private fun disconnect(remove: Boolean) {
        val old = connection
        connectionToken++; connection = null; proxy = null; bindTimeout?.cancel(); bindTimeout = null
        if (old != null && runCatching { Shizuku.pingBinder() }.getOrDefault(false)) runCatching { Shizuku.unbindUserService(args, old, remove) }
        _state.value = _state.value.copy(connection = if (_state.value.granted) PrivilegedConnection.SERVER_READY else PrivilegedConnection.DISCONNECTED)
    }

    fun close() {
        closed = true; loop?.cancel(); loop = null
        main.post {
            disconnect(true)
            if (initialized) { Shizuku.removeBinderReceivedListener(binderReceived); Shizuku.removeBinderDeadListener(binderDead); Shizuku.removeRequestPermissionResultListener(permissionResult) }
            initialized = false; gate.close()
        }
    }
    companion object { private const val REQUEST_CODE = 6101 }
}
