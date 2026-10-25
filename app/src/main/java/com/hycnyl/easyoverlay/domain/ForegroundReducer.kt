package com.hycnyl.easyoverlay.domain

enum class ForegroundKind { RESUMED, PAUSED, SCREEN_OFF }
data class ForegroundEvent(val timestamp: Long, val packageName: String, val className: String,
    val kind: ForegroundKind, val instanceId: Int = 0)

/** Overlapping query windows are deduplicated; a late pause for A can never close B. */
class ForegroundReducer(private val pauseGraceMs: Long = 350) {
    private val seen = LinkedHashSet<ForegroundEvent>()
    private var watermark = Long.MIN_VALUE
    private var current: ForegroundEvent? = null
    private var pendingPause: ForegroundEvent? = null
    val packageName: String? get() = current?.packageName
    var lastObservationAt: Long = 0
        private set

    fun accept(events: List<ForegroundEvent>, now: Long): String? {
        for (event in events.sortedBy { it.timestamp }) {
            if (!seen.add(event) || event.timestamp < watermark) continue
            watermark = event.timestamp
            lastObservationAt = event.timestamp
            when (event.kind) {
                ForegroundKind.RESUMED -> { current = event; pendingPause = null }
                ForegroundKind.SCREEN_OFF -> { current = null; pendingPause = null }
                ForegroundKind.PAUSED -> {
                    if (event.packageName == current?.packageName &&
                        (event.className == current?.className || event.className.isBlank()) &&
                        (event.instanceId == 0 || current?.instanceId == 0 || event.instanceId == current?.instanceId)) {
                        pendingPause = event
                    }
                }
            }
        }
        pendingPause?.let { if (now - it.timestamp >= pauseGraceMs) { current = null; pendingPause = null } }
        val cutoff = now - 10_000
        seen.removeAll { it.timestamp < cutoff }
        while (seen.size > 2048) seen.remove(seen.first())
        return packageName
    }

    fun reset() { seen.clear(); watermark = Long.MIN_VALUE; current = null; pendingPause = null; lastObservationAt = 0 }
}
