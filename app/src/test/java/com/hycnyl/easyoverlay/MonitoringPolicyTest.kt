package com.hycnyl.easyoverlay

import com.hycnyl.easyoverlay.domain.ForegroundEvent
import com.hycnyl.easyoverlay.domain.ForegroundKind
import com.hycnyl.easyoverlay.platform.monitoring.*
import org.junit.Assert.*
import org.junit.Test

class MonitoringPolicyTest {
    @Test fun autoUsesConnectedAccessibilityWithoutUsagePermission() {
        val availability = MonitoringAvailability(accessibilityConnected = true, shizukuAvailable = true)
        assertTrue(availability.anyAvailable)
        assertEquals(MonitoringSource.ACCESSIBILITY, MonitoringRouter.select(MonitoringMode.AUTO, availability).source)
        assertNull(MonitoringRouter.select(MonitoringMode.AUTO, availability).fallbackReason)
    }

    @Test fun explicitUsageDoesNotReadAccessibilityOrShizuku() {
        val privilegedOnly = MonitoringAvailability(accessibilityConnected = true, shizukuAvailable = true)
        assertEquals(SourceSelection(MonitoringSource.NONE, "USAGE_PERMISSION_MISSING"),
            MonitoringRouter.select(MonitoringMode.USAGE, privilegedOnly))
        assertEquals(listOf(MonitoringSource.USAGE), MonitoringRouter.priority(MonitoringMode.USAGE))
    }

    @Test fun failedPreferredSourceFallsBackAndReturnsWhenHealthy() {
        val all = MonitoringAvailability(true, true, true)
        assertEquals(SourceSelection(MonitoringSource.SHIZUKU, "ACCESSIBILITY_UNRESPONSIVE"),
            MonitoringRouter.select(MonitoringMode.AUTO, all, setOf(MonitoringSource.ACCESSIBILITY)))
        assertEquals(SourceSelection(MonitoringSource.USAGE, "ACCESSIBILITY_UNRESPONSIVE"),
            MonitoringRouter.select(MonitoringMode.AUTO, all, setOf(MonitoringSource.ACCESSIBILITY, MonitoringSource.SHIZUKU)))
        assertEquals(MonitoringSource.ACCESSIBILITY, MonitoringRouter.select(MonitoringMode.AUTO, all).source)
        assertEquals(SourceSelection(MonitoringSource.ACCESSIBILITY, "SHIZUKU_UNAVAILABLE"),
            MonitoringRouter.select(MonitoringMode.SHIZUKU, MonitoringAvailability(true, false, true)))
    }

    @Test fun noAuthorizedSourceReportsMissingPermissionRatherThanMissingEvents() {
        assertEquals(SourceSelection(MonitoringSource.NONE, "ACCESSIBILITY_NOT_CONNECTED"),
            MonitoringRouter.select(MonitoringMode.AUTO, MonitoringAvailability()))
        assertEquals(MonitoringSource.USAGE,
            MonitoringRouter.select(MonitoringMode.AUTO, MonitoringAvailability(usageGranted = true), setOf(MonitoringSource.USAGE)).source)
    }

    @Test fun oldModeAndOutOfOrderQueryCannotReplaceNewerObservation() {
        val gate = ObservationGate()
        val first = gate.newEpoch(100)
        assertFalse(gate.accept(first, 99))
        assertTrue(gate.accept(first, 120))
        assertFalse(gate.canAccept(first, 110))
        assertFalse(gate.accept(first, 110))
        assertTrue(gate.accept(first, 120))
        val second = gate.newEpoch(200)
        assertFalse(gate.isCurrent(first, 250))
        assertFalse(gate.accept(first, 250))
        assertFalse(gate.accept(second, 199))
        assertTrue(gate.accept(second, 200))
    }

    @Test fun focusedApplicationWinsOverHigherLayerOverlayAndInactiveWindows() {
        val windows = listOf(window("game.active", focused = true, layer = 5),
            window("game.inactive", layer = 10), window(null, type = 4, active = true, layer = 20))
        assertEquals(mapOf(0 to "game.active"), AccessibilityWindowSelector.select(windows))
    }

    @Test fun keyboardAndNotificationShadeRetainTheUnderlyingApplication() {
        val previous = mapOf(0 to "game.active")
        val keyboard = listOf(window("game.active", layer = 5), window("game.inactive", layer = 10),
            window(null, type = 2, focused = true, active = true, layer = 20))
        assertEquals(previous, AccessibilityWindowSelector.select(keyboard, previous))
        assertEquals(previous, AccessibilityWindowSelector.select(listOf(window(null, type = 3, focused = true)), previous))
    }

    @Test fun ownApplicationCanClearRuleAndDisplaysKeepIndependentFocus() {
        val windows = listOf(window("com.hycnyl.easyoverlay", focused = true),
            window("game.external", display = 7, focused = true), window(null, display = 7, type = 4, layer = 20))
        assertEquals(mapOf(0 to "com.hycnyl.easyoverlay", 7 to "game.external"), AccessibilityWindowSelector.select(windows))
    }

    @Test fun unknownApplicationRootIsNotMistakenForPreviousApplication() {
        val windows = listOf(window(null, focused = true), window(null, type = 2, active = true))
        assertTrue(AccessibilityWindowSelector.select(windows, mapOf(0 to "game.previous")).isEmpty())
        assertTrue(AccessibilityWindowSelector.select(emptyList(), mapOf(0 to "game.previous")).isEmpty())
    }

    @Test fun coldStartAndUnlockSearchHistoryRatherThanOnlyTheLastFewSeconds() {
        val now = 200_000_000L
        val oldResume = now - 30 * 60_000
        assertTrue(UsageQueryWindow.start(now, null, false) < oldResume)
        assertTrue(UsageQueryWindow.start(now, now - 100, true) < oldResume)
        assertEquals(now - 5100, UsageQueryWindow.start(now, now - 100, false))
        assertEquals(now - UsageQueryWindow.RECALIBRATION_LOOKBACK, UsageQueryWindow.start(now, now + 100, false))
        assertEquals(0, UsageQueryWindow.start(100, null, true))
    }

    @Test fun unlockWithoutAnotherResumeRestoresTheMostRecentlyResumedApp() {
        val history = UsageResumeHistory()
        val events = listOf(event(1000, "game.a"), event(2000, "game.b"),
            event(3000, "game.b", ForegroundKind.PAUSED), event(3050, "", ForegroundKind.SCREEN_OFF))
        assertEquals("game.b", history.accept(events, 120_000))
        assertEquals(2000, history.lastObservationAt)
        assertEquals("game.b", history.accept(events, 120_500))
        assertEquals("launcher", history.accept(listOf(event(121_000, "launcher")), 121_000))
        assertEquals("launcher", history.accept(listOf(event(1500, "late.old.app")), 121_100))
    }

    @Test fun cachedPrivilegedObservationExpiresAndCannotComeFromFutureClock() {
        assertTrue(PrivilegedSnapshotFreshness.isFresh(10_000, 10_000))
        assertTrue(PrivilegedSnapshotFreshness.isFresh(7_000, 10_000))
        assertFalse(PrivilegedSnapshotFreshness.isFresh(6_999, 10_000))
        assertFalse(PrivilegedSnapshotFreshness.isFresh(11_501, 10_000))
    }

    private fun window(pkg: String?, display: Int = 0, type: Int = 1,
        focused: Boolean = false, active: Boolean = false, layer: Int = 1) =
        ForegroundWindow(display, layer, type, focused, active, layer, pkg)

    private fun event(time: Long, pkg: String, kind: ForegroundKind = ForegroundKind.RESUMED) =
        ForegroundEvent(time, pkg, "Main", kind)
}
