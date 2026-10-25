package com.hycnyl.easyoverlay.domain

object LibraryValidator {
    private val uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".toRegex()
    fun validate(state: LibraryState) {
        require(state.schemaVersion == 1) { "备份版本不受支持，请更新 EasyOverlay" }
        require(state.assets.size <= 2000 && state.rules.size <= 2000 && state.unassigned.size <= 4000) { "图片或规则数量过多" }
        require(state.assets.map { it.id }.distinct().size == state.assets.size) { "图片 ID 重复" }
        require(state.rules.map { it.key }.distinct().size == state.rules.size) { "同一应用和屏幕存在重复规则" }
        require(state.quickSelections.map { it.displayId }.distinct().size == state.quickSelections.size) { "快速使用屏幕重复" }
        val ids = state.assets.map { it.id }.toSet()
        state.assets.forEach {
            require(uuid.matches(it.id)) { "图片 ID 无效" }
            val ext = when (it.mimeType) { "image/png" -> "png"; "image/jpeg" -> "jpg"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> error("图片类型不受支持") }
            require(it.relativePath == "assets/${it.id}.$ext") { "图片路径无效" }
            require(it.width in 1..16384 && it.height in 1..16384 && it.width.toLong() * it.height <= 64_000_000) { "图片尺寸超出限制" }
            require(it.sha256.matches("[0-9a-f]{64}".toRegex()) && it.byteCount in 1..33_554_432) { "图片校验信息无效" }
        }
        state.rules.forEach {
            require(it.packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+".toRegex())) { "应用包名无效" }
            require(it.assetId in ids && it.displayId >= 0 && it.opacityPercent in 0..100) { "应用规则引用无效" }
        }
        state.quickSelections.forEach { require(it.assetId in ids && it.displayId >= 0 && it.opacityPercent in 0..100) { "快速使用图片引用无效" } }
        state.unassigned.forEach { require(it.assetId in ids && it.displayId >= 0) { "待分配图片引用无效" } }
    }
}
