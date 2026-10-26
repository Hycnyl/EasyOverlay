package com.hycnyl.easyoverlay.platform.privileged

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** One daemon worker per controller. A stuck Binder call never spawns an unbounded replacement pool. */
internal class BinderCallGate {
    private val running = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "EasyOverlay-privileged-binder").apply { isDaemon = true } }
    suspend fun <T> call(timeoutMs: Long, block: () -> T): T = withContext(Dispatchers.IO) {
        check(running.compareAndSet(false, true)) { "PRIVILEGED_CALL_BUSY" }
        val future = try { executor.submit<T> { try { block() } finally { running.set(false) } } }
        catch (error: Exception) { running.set(false); throw error }
        try { future.get(timeoutMs, TimeUnit.MILLISECONDS) }
        catch (error: Exception) { future.cancel(true); throw error }
    }
    fun close() { executor.shutdownNow() }
}
