package com.hycnyl.easyoverlay

import com.hycnyl.easyoverlay.domain.*
import org.junit.Assert.*
import org.junit.Test

class PolicyTest {
    private val a = AppRule("game.a", 0, "asset-a")
    private val b = AppRule("game.b", 0, "asset-b", 70)
    private fun resolve(foreground: String?, manual: Map<Int, OverlayTarget> = emptyMap(), auto: Boolean = true) =
        RuleResolver.resolve(setOf(0, 8), manual, listOf(a, b), foreground, auto, "com.hycnyl.easyoverlay", 0.8f)

    @Test fun manualSurvivesAppChangesAndStoppingResolvesLatestForeground() {
        val manual = mapOf(0 to OverlayTarget(0, OverlayOwner.QUICK_USE, "manual", 60))
        for (pkg in listOf("game.a", "game.b", "game.c")) assertEquals("manual", resolve(pkg, manual)[0]?.assetId)
        assertEquals("asset-b", resolve("game.b")[0]?.assetId)
        assertTrue(resolve("game.c").isEmpty())
    }
    @Test fun automaticDisableDoesNotTouchOtherDisplayManualSession() {
        val manual = mapOf(8 to OverlayTarget(8, OverlayOwner.QUICK_USE, "second", 60))
        val state = resolve("game.a", manual, false)
        assertEquals(setOf(8), state.keys)
        assertEquals(OverlayOwner.QUICK_USE, state[8]?.owner)
    }
    @Test fun ownAppAndUnknownForegroundDoNotReplayAnOldRule() {
        assertTrue(resolve(null).isEmpty())
        assertTrue(resolve("com.hycnyl.easyoverlay").isEmpty())
        assertEquals("asset-b", resolve("game.b")[0]?.assetId)
    }
    @Test fun opacityUsesFloorAndMatchesWindowSafetyLimit() {
        assertEquals(80, OpacityPolicy.maxPercent(0.8f))
        assertEquals(73, OpacityPolicy.maxPercent(0.739f))
        assertEquals(80, OpacityPolicy.clamp(100, 0.8f))
        assertEquals(0, OpacityPolicy.maxPercent(Float.NaN))
        assertEquals(0.8f, OpacityPolicy.combinedAlpha(listOf(0.6f, 0.5f)), 0.00001f)
    }
    @Test fun secondaryRulesNeverUsePrimaryScreenGuess() {
        val targets = RuleResolver.resolve(setOf(0,8), emptyMap(), listOf(a.copy(displayId=8)), "game.a", true, "self.app", 0.8f)
        assertTrue(targets.isEmpty())
    }
}
