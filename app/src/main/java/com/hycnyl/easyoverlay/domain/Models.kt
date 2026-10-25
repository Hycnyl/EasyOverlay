package com.hycnyl.easyoverlay.domain

data class OverlayAsset(
    val id: String,
    val relativePath: String,
    val mimeType: String,
    val isAnimatedGif: Boolean,
    val width: Int,
    val height: Int,
    val createdAt: Long,
    val sha256: String,
    val byteCount: Long
)

data class AppRule(
    val packageName: String,
    val displayId: Int,
    val assetId: String,
    val opacityPercent: Int = 60,
    val autoEnabled: Boolean = true
) {
    val key: String get() = "$packageName@$displayId"
}

data class QuickSelection(val displayId: Int, val assetId: String, val opacityPercent: Int = 60)

data class UnassignedAsset(
    val assetId: String,
    val label: String,
    val reason: String,
    val suggestedPackage: String? = null,
    val displayId: Int = 0,
    val migrationKey: String? = null
)

data class LibraryState(
    val schemaVersion: Int = 1,
    val assets: List<OverlayAsset> = emptyList(),
    val rules: List<AppRule> = emptyList(),
    val quickSelections: List<QuickSelection> = emptyList(),
    val unassigned: List<UnassignedAsset> = emptyList(),
    val importedKeys: Set<String> = emptySet()
)

enum class OverlayOwner { QUICK_USE, APP_RULE }

data class OverlayTarget(
    val displayId: Int,
    val owner: OverlayOwner,
    val assetId: String,
    val opacityPercent: Int,
    val sourcePackageName: String? = null
)

data class ActiveOverlaySession(val target: OverlayTarget, val mountedAt: Long)

data class RuntimeState(
    val serviceRunning: Boolean = false,
    val autoMonitoring: Boolean = false,
    val foregroundPackage: String? = null,
    val observedAt: Long = 0,
    val sessions: Map<Int, ActiveOverlaySession> = emptyMap(),
    val pendingDisplays: Set<Int> = emptySet(),
    val failures: Map<Int, String> = emptyMap(),
    val monitorProblem: String? = null,
    val floatingVisible: Boolean = false
)
