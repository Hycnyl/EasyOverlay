package com.hycnyl.easyoverlay.data

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.hycnyl.easyoverlay.domain.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** One AtomicFile commit contains every asset reference. Source directories are never touched. */
class LibraryRepository(context: Context) {
    private val root = context.filesDir
    private val index = AtomicFile(File(root, "library-v1.json"))
    private val gson = Gson()
    private val lock = Mutex()
    private val initialized = CompletableDeferred<Unit>()
    private val _state = MutableStateFlow(LibraryState())
    val state = _state.asStateFlow()
    private val _problem = MutableStateFlow<String?>(null)
    val problem = _problem.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        lock.withLock {
            if (initialized.isCompleted) return@withLock
            try {
                if (index.baseFile.exists() || File(index.baseFile.path + ".bak").exists()) {
                    val value = index.openRead().use { input ->
                        require(input.available() <= 8 * 1024 * 1024) { "图片索引过大" }
                        gson.fromJson(input.reader(Charsets.UTF_8), LibraryState::class.java)
                    } ?: error("图片索引为空")
                    LibraryValidator.validate(value)
                    _state.value = value
                }
            } catch (_: Exception) {
                _problem.value = "图片索引无法读取，已保护原文件。请先导出诊断，再导入 EasyOverlay 备份进行修复。"
            } finally {
                _ready.value = true
                initialized.complete(Unit)
            }
        }
    }

    fun assetFile(asset: OverlayAsset): File {
        val file = File(root, asset.relativePath)
        require(file.canonicalPath.startsWith(File(root, "assets").canonicalPath + File.separator)) { "图片路径无效" }
        return file
    }

    suspend fun change(transform: (LibraryState) -> LibraryState) = withContext(Dispatchers.IO) {
        initialized.await()
        lock.withLock {
            check(_problem.value == null) { _problem.value.orEmpty() }
            commit(transform(_state.value))
        }
    }

    private fun commit(value: LibraryState) {
        LibraryValidator.validate(value)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = index.startWrite()
            stream.write(gson.toJson(value).toByteArray(Charsets.UTF_8))
            index.finishWrite(stream)
            _state.value = value
        } catch (e: Exception) {
            if (stream != null) index.failWrite(stream)
            throw java.io.IOException("保存失败，请检查可用存储空间后重试。原配置已保留。", e)
        }
    }

    suspend fun install(asset: OverlayAsset, prepared: File): OverlayAsset = withContext(Dispatchers.IO) {
        initialized.await()
        lock.withLock {
            check(_problem.value == null) { _problem.value.orEmpty() }
            val duplicate = _state.value.assets.firstOrNull { it.sha256 == asset.sha256 && assetFile(it).isFile }
            if (duplicate != null) { prepared.delete(); return@withLock duplicate }
            val destination = assetFile(asset)
            check(destination.parentFile?.mkdirs() == true || destination.parentFile?.isDirectory == true) { "无法创建私有图片目录" }
            check(!destination.exists() && prepared.renameTo(destination)) { "无法保存图片，请检查存储空间" }
            try { commit(_state.value.copy(assets = _state.value.assets + asset)) }
            catch (e: Exception) { destination.delete(); throw e }
            asset
        }
    }

    suspend fun selectQuick(displayId: Int, assetId: String?, opacity: Int = 60) = change { current ->
        current.copy(quickSelections = current.quickSelections.filterNot { it.displayId == displayId } +
            if (assetId == null) emptyList() else listOf(QuickSelection(displayId, assetId, opacity)))
    }

    suspend fun saveRule(rule: AppRule) = change { current ->
        current.copy(rules = current.rules.filterNot { it.key == rule.key } + rule)
    }

    suspend fun removeRule(key: String) = change { it.copy(rules = it.rules.filterNot { rule -> rule.key == key }) }

    suspend fun deleteUnusedAsset(id: String) = withContext(Dispatchers.IO) {
        initialized.await()
        lock.withLock {
            check(_problem.value == null) { _problem.value.orEmpty() }
            val current = _state.value
            require(current.rules.none { it.assetId == id } && current.quickSelections.none { it.assetId == id }) {
                "这张图片仍被规则或快速使用引用，请先更换图片或移除引用"
            }
            val asset = current.assets.firstOrNull { it.id == id } ?: return@withLock
            commit(current.copy(assets = current.assets.filterNot { it.id == id }, unassigned = current.unassigned.filterNot { it.assetId == id }))
            // The reference transaction commits first. No directory-wide deletion, including on failure.
            assetFile(asset).delete()
        }
    }

    suspend fun exportSnapshot(): LibraryState = withContext(Dispatchers.IO) {
        initialized.await()
        lock.withLock { check(_problem.value == null) { _problem.value.orEmpty() }; _state.value }
    }

    /** Explicit backup restore is the only operation allowed to replace a protected corrupt index. */
    suspend fun mergeBackup(incoming: LibraryState, files: Map<String, File>) = withContext(Dispatchers.IO) {
        initialized.await()
        lock.withLock {
            LibraryValidator.validate(incoming)
            val current = _state.value
            val byHash = current.assets.associateBy { it.sha256 }
            val idMap = mutableMapOf<String, String>()
            val added = mutableListOf<OverlayAsset>()
            val created = mutableListOf<File>()
            try {
                incoming.assets.forEach { asset ->
                    val existing = byHash[asset.sha256]?.takeIf { assetFile(it).isFile }
                    if (existing != null) idMap[asset.id] = existing.id
                    else {
                        val freshId = java.util.UUID.randomUUID().toString()
                        val fresh = asset.copy(id = freshId, relativePath = "assets/$freshId.${asset.relativePath.substringAfterLast('.')}")
                        val destination = assetFile(fresh)
                        destination.parentFile?.mkdirs()
                        check(files[asset.id]?.renameTo(destination) == true) { "备份图片无法保存" }
                        created += destination; added += fresh; idMap[asset.id] = freshId
                    }
                }
                val rules = current.rules.toMutableList()
                val unassigned = current.unassigned.toMutableList()
                incoming.rules.forEach { rule ->
                    val mapped = rule.copy(assetId = idMap.getValue(rule.assetId))
                    val existing = rules.firstOrNull { it.key == mapped.key }
                    if (existing == null) rules += mapped
                    else if (existing != mapped && unassigned.none { it.assetId == mapped.assetId && it.suggestedPackage == mapped.packageName && it.displayId == mapped.displayId }) {
                        unassigned += UnassignedAsset(mapped.assetId, mapped.packageName, "备份规则与现有规则冲突，请在图片库中手动分配", mapped.packageName, mapped.displayId)
                    }
                }
                incoming.unassigned.forEach { item ->
                    val mapped = item.copy(assetId = idMap.getValue(item.assetId))
                    if (mapped !in unassigned) unassigned += mapped
                }
                val quick = current.quickSelections + incoming.quickSelections.filter { item -> current.quickSelections.none { it.displayId == item.displayId } }
                    .map { it.copy(assetId = idMap.getValue(it.assetId)) }
                if (_problem.value != null && index.baseFile.exists()) {
                    index.baseFile.copyTo(File(root, "protected-index-${System.currentTimeMillis()}.json"))
                }
                commit(current.copy(assets = current.assets + added, rules = rules, quickSelections = quick,
                    unassigned = unassigned.distinct()))
                _problem.value = null
            } catch (e: Exception) { created.forEach { it.delete() }; throw e }
        }
    }
}
