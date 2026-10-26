package com.hycnyl.easyoverlay

import com.hycnyl.easyoverlay.domain.*
import com.hycnyl.easyoverlay.platform.OverlayCapabilityState
import org.junit.Assert.*
import org.junit.Test

class TrustedOpacityPolicyTest {
    @Test fun trustedPrimaryAllowsFullAlphaWithoutUnlockingSecondaryWindows() {
        val manual = mapOf(
            0 to OverlayTarget(0, OverlayOwner.QUICK_USE, "primary", 95),
            7 to OverlayTarget(7, OverlayOwner.QUICK_USE, "secondary", 95)
        )
        val targets = RuleResolver.resolve(setOf(0,7), manual, emptyList(), null, false,
            "self.app", 0.8f, maximumAlphaForDisplay = { id -> if (id == 0) 1f else 0.8f })
        assertEquals(95, targets[0]?.opacityPercent)
        assertEquals(80, targets[7]?.opacityPercent)
    }

    @Test fun automaticAndQuickUseShareTheSameTrustedLimit() {
        val rules = listOf(AppRule("game.one", 0, "rule", 100))
        val automatic = RuleResolver.resolve(setOf(0), emptyMap(), rules, "game.one", true,
            "self.app", 0.8f, maximumAlphaForDisplay = { 1f })
        assertEquals(100, automatic[0]?.opacityPercent)
        val quick = mapOf(0 to OverlayTarget(0, OverlayOwner.QUICK_USE, "manual", 100))
        val overridden = RuleResolver.resolve(setOf(0), quick, rules, "game.one", true,
            "self.app", 0.8f, maximumAlphaForDisplay = { 1f })
        assertEquals(OverlayOwner.QUICK_USE, overridden[0]?.owner)
        assertEquals(100, overridden[0]?.opacityPercent)
    }

    @Test fun disconnectSafelyRecomputesPersistedHighOpacity() {
        val quick = mapOf(0 to OverlayTarget(0, OverlayOwner.QUICK_USE, "manual", 97))
        fun targets(connected: Boolean) = RuleResolver.resolve(setOf(0), quick, emptyList(), null,
            false, "self.app", 0.8f, maximumAlphaForDisplay = { if (connected) 1f else 0.8f })
        assertEquals(97, targets(true)[0]?.opacityPercent)
        assertEquals(80, targets(false)[0]?.opacityPercent)
        assertEquals(OverlayOwner.QUICK_USE, targets(false)[0]?.owner)
    }

    @Test fun highOpacityWarningRemainsStrictlyAboveEightyRegardlessOfWindowTrust() {
        assertFalse(OpacityPolicy.requiresHighOpacityWarning(80))
        assertTrue(OpacityPolicy.requiresHighOpacityWarning(81))
        assertTrue(OpacityPolicy.requiresHighOpacityWarning(100))
        assertEquals(100, OpacityPolicy.clamp(100, 1f))
        assertEquals(80, OpacityPolicy.clamp(100, 0.8f))
    }

    @Test fun enabledSettingWithoutLiveConnectionNeverAdvertisesTrustedOpacity() {
        val notConnected = OverlayCapabilityState(80, false, true, 1)
        assertEquals(80, notConnected.maximumPercent(0))
        assertEquals("ACCESSIBILITY_NOT_CONNECTED", notConnected.reasonCode(0))
        val connected = notConnected.copy(accessibilityConnected = true)
        assertEquals(100, connected.maximumPercent(0))
        assertEquals(80, connected.maximumPercent(7))
        assertEquals("TRUSTED_SECONDARY_UNVERIFIED", connected.reasonCode(7))
    }
}
