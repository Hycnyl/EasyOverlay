package com.hycnyl.easyoverlay.platform.privileged

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/** One daemon worker per controller. A stuck Binder call never spawns an unbounded replacement pool. */
internal class BinderCallGate {
    private val running = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "EasyOverlay-privileged-binder").apply { isDaemon = true } }
    suspend fun <T> call(timeoutMs: Long, block: () -> T): T = withContext(Dispatchers.IO) {
        val deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        // Let an explicit preview wait for a short in-flight foreground query. Never add workers
        // to bypass a genuinely stuck Binder call; the same deadline bounds queueing and execution.
        while(!running.compareAndSet(false,true)) {
            if(System.nanoTime()>=deadline)throw TimeoutException("PRIVILEGED_CALL_BUSY")
            delay(20)
        }
        val future = try { executor.submit<T> { try { block() } finally { running.set(false) } } }
        catch (error: Exception) { running.set(false); throw error }
        try { future.get((deadline-System.nanoTime()).coerceAtLeast(1), TimeUnit.NANOSECONDS) }
        catch (error: Exception) { future.cancel(true); throw error }
    }
    fun close() { executor.shutdownNow() }
}
