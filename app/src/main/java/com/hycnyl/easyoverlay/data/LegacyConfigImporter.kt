package com.hycnyl.easyoverlay.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.hycnyl.easyoverlay.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScannedLegacyItem(val item: LegacyItem, val source: Uri?, val note: String)
data class LegacyScan(val plan: LegacyPlan, val items: List<ScannedLegacyItem>)
data class MigrationReport(val imported: Int, val skipped: Int, val failed: List<String>)

class LegacyConfigImporter(private val context: Context, private val library: LibraryRepository,
    private val images: ImageImporter) {
    suspend fun scan(tree: Uri): LegacyScan = withContext(Dispatchers.IO) {
        val granted = DocumentFile.fromTreeUri(context, tree) ?: error("目录授权无效，请重新选择旧文件夹")
        val root = if (granted.findFile("config.json") != null) granted else granted.findFile("ImageOverlay")
            ?: error("此目录没有 ImageOverlay/config.json，请选择原版保存目录")
        val config = root.findFile("config.json") ?: error("找不到旧版 config.json")
        val json = context.contentResolver.openInputStream(config.uri)?.use {
            val bytes = it.readNBytes(8 * 1024 * 1024 + 1)
            require(bytes.size <= 8 * 1024 * 1024) { "旧配置文件过大" }
            bytes.toString(Charsets.UTF_8)
        } ?: error("旧配置无法读取，请检查目录授权")
        val plan = LegacyPlanner.parse(json)
        val paths = linkedMapOf<String, Uri>()
        val documentIds = mutableMapOf<String, Uri>()
        val visited = mutableSetOf<String>()
        fun visit(directory: DocumentFile, prefix: String, depth: Int) {
            require(depth <= 8) { "旧目录层级过深，请选择 ImageOverlay 目录" }
            if (!visited.add(directory.uri.toString())) return
            for (file in directory.listFiles()) {
                require(paths.size + visited.size < 6000) { "旧目录文件过多，请单独选择 ImageOverlay 文件夹" }
                val name = file.name ?: continue
                val path = prefix + name
                if (file.isDirectory) visit(file, "$path/", depth + 1)
                else {
                    paths[path] = file.uri
                    runCatching { DocumentsContract.getDocumentId(file.uri) }.getOrNull()?.let { documentIds[it] = file.uri }
                }
            }
        }
        visit(root, "", 0)
        val scanned = plan.items.map { item ->
            // Old content URIs are accepted only after matching a document discovered in the authorized tree.
            val exact = runCatching {
                val old = Uri.parse(item.imageReference)
                if (old.scheme == "content" && old.authority == root.uri.authority)
                    documentIds[DocumentsContract.getDocumentId(old)] else null
            }.getOrNull()
            val matches = LegacyPlanner.relativeCandidates(item.imageReference, item.groupName, item.configName)
                .mapNotNull { paths[it] }.distinct()
            val source = exact ?: matches.singleOrNull()
            val note = when {
                item.key in library.state.value.importedKeys -> "已导入，将跳过"
                source == null && matches.size > 1 -> "同名图片不唯一，请导入后手动选图"
                source == null -> "未在授权目录找到图片，可修复源文件后重试"
                item.packageName == null -> "未关联应用，将放入待分配图片"
                item.secondary -> "副屏规则需在连接副屏后手动分配"
                item.key !in plan.candidates -> "存在多个候选配置，将放入待分配图片"
                else -> "将创建应用规则（自动启用默认关闭）"
            }
            ScannedLegacyItem(item, source, note)
        }
        LegacyScan(plan, scanned)
    }

    suspend fun import(scan: LegacyScan): MigrationReport = withContext(Dispatchers.IO) {
        var imported = 0; var skipped = 0
        val failures = mutableListOf<String>()
        for (row in scan.items) {
            val item = row.item
            if (item.key in library.state.value.importedKeys) { skipped++; continue }
            if (row.source == null) { failures += "${item.groupName} / ${item.configName}：未找到可读取的唯一图片"; continue }
            try {
                val asset = images.import(row.source)
                library.change { state ->
                    if (item.key in state.importedKeys) return@change state
                    val rule = item.packageName?.let { AppRule(it, 0, asset.id, 60, false) }
                    val canAssign = !item.secondary && item.key in scan.plan.candidates && rule != null && state.rules.none { it.key == rule.key }
                    state.copy(
                        rules = state.rules + if (canAssign) listOf(rule!!) else emptyList(),
                        unassigned = state.unassigned + if (canAssign) emptyList() else listOf(UnassignedAsset(asset.id,
                            "${item.groupName} / ${item.configName}", if (item.secondary) "副屏未分配，请连接目标屏幕后选择" else row.note,
                            item.packageName, 0, item.key)),
                        importedKeys = state.importedKeys + item.key
                    )
                }
                imported++
            } catch (_: Exception) { failures += "${item.groupName} / ${item.configName}：读取、解码或保存失败，可检查图片后重试" }
        }
        MigrationReport(imported, skipped, failures)
    }
}
