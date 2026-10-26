package com.hycnyl.easyoverlay.platform.monitoring

enum class MonitoringMode { AUTO, ACCESSIBILITY, SHIZUKU, USAGE }
enum class MonitoringSource { NONE, ACCESSIBILITY, SHIZUKU, USAGE }

data class MonitoringAvailability(
    val accessibilityConnected: Boolean = false,
    val shizukuAvailable: Boolean = false,
    val usageGranted: Boolean = false
) {
    val anyAvailable: Boolean get() = accessibilityConnected || shizukuAvailable || usageGranted
    fun available(source: MonitoringSource): Boolean = when (source) {
        MonitoringSource.ACCESSIBILITY -> accessibilityConnected
        MonitoringSource.SHIZUKU -> shizukuAvailable
        MonitoringSource.USAGE -> usageGranted
        MonitoringSource.NONE -> false
    }
}

data class MonitoringState(
    val requestedMode: MonitoringMode = MonitoringMode.AUTO,
    val selectedSource: MonitoringSource = MonitoringSource.NONE,
    val running: Boolean = false,
    val lastPackage: String? = null,
    val lastEventAt: Long = 0,
    val packagesByDisplay: Map<Int, String> = emptyMap(),
    val problem: String? = null,
    val fallbackReason: String? = null
)

/** The optional Shizuku adapter supplies only foreground metadata, never screen text. */
data class PrivilegedSnapshot(
    val packageName: String?,
    val displayId: Int = 0,
    val observedAtMillis: Long = System.currentTimeMillis(),
    val problem: String? = null,
    val packagesByDisplay: Map<Int, String> = packageName?.let { mapOf(displayId to it) }.orEmpty()
)

internal data class SourceSnapshot(
    val packageName: String?,
    val observedAtMillis: Long,
    val capturedAtElapsed: Long,
    val packagesByDisplay: Map<Int, String> = packageName?.let { mapOf(0 to it) }.orEmpty(),
    val problem: String? = null
)

data class SourceSelection(val source: MonitoringSource, val fallbackReason: String? = null)

/** Source preference is explicit. Selecting Usage never silently enables privileged readers. */
object MonitoringRouter {
    fun priority(mode: MonitoringMode): List<MonitoringSource> = when (mode) {
        MonitoringMode.AUTO -> listOf(MonitoringSource.ACCESSIBILITY, MonitoringSource.SHIZUKU, MonitoringSource.USAGE)
        MonitoringMode.ACCESSIBILITY -> listOf(MonitoringSource.ACCESSIBILITY, MonitoringSource.USAGE)
        MonitoringMode.SHIZUKU -> listOf(MonitoringSource.SHIZUKU, MonitoringSource.ACCESSIBILITY, MonitoringSource.USAGE)
        MonitoringMode.USAGE -> listOf(MonitoringSource.USAGE)
    }

    fun select(mode: MonitoringMode, availability: MonitoringAvailability,
        unhealthy: Set<MonitoringSource> = emptySet()): SourceSelection {
        val priority = priority(mode)
        val authorized = priority.filter { availability.available(it) }
        val selected = authorized.firstOrNull { it !in unhealthy } ?: authorized.firstOrNull() ?: MonitoringSource.NONE
        val reason = if (selected == priority.firstOrNull()) null else when (priority.first()) {
            MonitoringSource.ACCESSIBILITY -> if (availability.accessibilityConnected) "ACCESSIBILITY_UNRESPONSIVE" else "ACCESSIBILITY_NOT_CONNECTED"
            MonitoringSource.SHIZUKU -> if (availability.shizukuAvailable) "SHIZUKU_QUERY_FAILED" else "SHIZUKU_UNAVAILABLE"
            MonitoringSource.USAGE -> "USAGE_PERMISSION_MISSING"
            MonitoringSource.NONE -> "MONITOR_PERMISSION_MISSING"
        }
        return SourceSelection(selected, reason)
    }
}

/** Drop a late result from an old mode or a query predating a newer accepted observation. */
class ObservationGate {
    private var epoch = 0L
    private var minimumAt = 0L
    private var lastAcceptedAt = 0L
    @Synchronized fun currentEpoch(): Long = epoch
    @Synchronized fun newEpoch(nowElapsed: Long): Long {
        epoch++; minimumAt = nowElapsed; lastAcceptedAt = nowElapsed
        return epoch
    }
    @Synchronized fun accept(queryEpoch: Long, capturedAtElapsed: Long): Boolean {
        if (!canAccept(queryEpoch, capturedAtElapsed)) return false
        lastAcceptedAt = capturedAtElapsed
        return true
    }
    @Synchronized fun canAccept(queryEpoch: Long, capturedAtElapsed: Long): Boolean =
        queryEpoch == epoch && capturedAtElapsed >= minimumAt && capturedAtElapsed >= lastAcceptedAt
    @Synchronized fun isCurrent(queryEpoch: Long, capturedAtElapsed: Long): Boolean =
        queryEpoch == epoch && capturedAtElapsed >= minimumAt
}

/** Pure selection, with Android window constants represented by their stable integer values. */
data class ForegroundWindow(val displayId: Int, val windowId: Int, val type: Int,
    val focused: Boolean, val active: Boolean, val layer: Int, val packageName: String?)

object AccessibilityWindowSelector {
    const val TYPE_APPLICATION = 1
    fun select(windows: List<ForegroundWindow>, previous: Map<Int, String> = emptyMap()): Map<Int, String> =
        windows.groupBy { it.displayId }.mapNotNull { (display, all) ->
            val applications = all.filter { it.type == TYPE_APPLICATION && !it.packageName.isNullOrBlank() }
            val selected = applications.filter { it.focused }.maxByOrNull { it.layer }
                ?: applications.filter { it.active }.maxByOrNull { it.layer }
                ?: if (all.any { it.type != TYPE_APPLICATION && (it.focused || it.active) })
                    applications.firstOrNull { it.packageName == previous[display] } ?: applications.maxByOrNull { it.layer }
                else applications.maxByOrNull { it.layer }
            val packageName = selected?.packageName ?: if (all.none { it.type == TYPE_APPLICATION } &&
                all.any { it.focused || it.active }) previous[display] else null
            packageName?.let { display to it }
        }.toMap()
}

object UsageQueryWindow {
    const val RECALIBRATION_LOOKBACK = 24L * 60 * 60 * 1000
    const val OVERLAP = 5_000L
    fun start(now: Long, cursor: Long?, recalibrate: Boolean): Long =
        if (cursor == null || cursor > now || recalibrate) (now - RECALIBRATION_LOOKBACK).coerceAtLeast(0)
        else (cursor - OVERLAP).coerceAtLeast(0)
}

/** Wall-clock age is also checked when an optional adapter returns a cached, rather than live, result. */
object PrivilegedSnapshotFreshness {
    const val MAX_AGE_MILLIS = 3_000L
    const val MAX_FUTURE_SKEW_MILLIS = 1_500L
    fun isFresh(observedAt: Long, now: Long): Boolean =
        observedAt >= now - MAX_AGE_MILLIS && observedAt <= now + MAX_FUTURE_SKEW_MILLIS
}
