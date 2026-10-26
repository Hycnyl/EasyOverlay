package com.hycnyl.easyoverlay.domain

import kotlin.math.floor

object OpacityPolicy {
    fun maxPercent(systemAlpha: Float): Int =
        if (systemAlpha.isFinite()) floor(systemAlpha.coerceIn(0f, 1f) * 100.0 + 0.0001).toInt() else 0

    fun clamp(percent: Int, systemAlpha: Float): Int = percent.coerceIn(0, maxPercent(systemAlpha))

    /** The user's persistent warning threshold is independent of a trusted window's permission. */
    fun requiresHighOpacityWarning(percent: Int): Boolean = percent > 80

    fun combinedAlpha(alphas: List<Float>): Float =
        1f - alphas.fold(1f) { remaining, alpha -> remaining * (1f - alpha.coerceIn(0f, 1f)) }
}

/** Pure desired-state calculation. Automatic observations are trustworthy only on display 0. */
object RuleResolver {
    fun resolve(
        displays: Set<Int>,
        manual: Map<Int, OverlayTarget>,
        rules: List<AppRule>,
        foregroundPackage: String?,
        autoEnabled: Boolean,
        ownPackage: String,
        systemAlpha: Float,
        suppressed: Set<Pair<Int, String>> = emptySet(),
        maximumAlphaForDisplay: (Int) -> Float = { systemAlpha }
    ): Map<Int, OverlayTarget> = buildMap {
        for (display in displays) {
            val quick = manual[display]
            if (quick != null) {
                put(display, quick.copy(opacityPercent = OpacityPolicy.clamp(quick.opacityPercent, maximumAlphaForDisplay(display))))
            } else if (display == 0 && autoEnabled && foregroundPackage != null && foregroundPackage != ownPackage &&
                (display to foregroundPackage) !in suppressed) {
                rules.firstOrNull { it.displayId == display && it.packageName == foregroundPackage && it.autoEnabled }
                    ?.let { rule -> put(display, OverlayTarget(display, OverlayOwner.APP_RULE, rule.assetId,
                        OpacityPolicy.clamp(rule.opacityPercent, maximumAlphaForDisplay(display)), rule.packageName)) }
            }
        }
    }
}
