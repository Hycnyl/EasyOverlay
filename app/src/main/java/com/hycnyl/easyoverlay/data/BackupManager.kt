package com.hycnyl.easyoverlay.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.hycnyl.easyoverlay.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupEnvelope(val product: String = "EasyOverlay", val formatVersion: Int = 1,
    val createdAt: Long = System.currentTimeMillis(), val library: LibraryState)
data class BackupPreview(val state: LibraryState, val files: Map<String, File>, val directory: File) {
    fun close() { directory.deleteRecursively() }
}

class BackupManager(private val context: Context, private val library: LibraryRepository, private val images: ImageImporter) {
    private val gson = Gson()

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val state = library.exportSnapshot()
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("无法写入备份，请重新选择保存位置")
        output.use { stream -> ZipOutputStream(stream.buffered()).use { zip ->
            state.assets.forEach { asset ->
                val source = library.assetFile(asset)
                require(source.isFile && source.length() == asset.byteCount) { "图片丢失或损坏，备份未完成。请在图片库中修复该图片后重试。" }
                zip.putNextEntry(ZipEntry(asset.relativePath))
                source.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("EasyOverlay-backup.json"))
            zip.write(gson.toJson(BackupEnvelope(library = state)).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        } }
    }

    suspend fun preview(uri: Uri): BackupPreview = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "backup-${java.util.UUID.randomUUID()}")
        check(directory.mkdirs()) { "无法创建临时备份目录" }
        try {
            val entries = mutableMapOf<String, File>()
            var total = 0L
            val input = context.contentResolver.openInputStream(uri) ?: error("无法读取备份，请重新选择文件")
            input.use { ZipInputStream(it.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    require(!entry.isDirectory && entries.size < 2001 && name !in entries) { "备份包含重复条目或数量超出限制" }
                    require(name == "EasyOverlay-backup.json" || name.matches("assets/[0-9a-f-]{36}\\.(png|jpg|webp|gif)".toRegex())) { "备份包含无效路径或未知文件" }
                    val target = File.createTempFile("entry-", ".part", directory)
                    var size = 0L
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count == -1) break
                            size += count; total += count
                            require(size <= if (name.endsWith(".json")) 8 * 1024 * 1024 else ImagePolicy.MAX_BYTES) { "备份中的文件过大" }
                            require(total <= 512L * 1024 * 1024) { "备份展开后超过 512 MB，请拆分图片后重试" }
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                    entries[name] = target
                    zip.closeEntry()
                }
            } }
            val manifest = entries.remove("EasyOverlay-backup.json") ?: error("不是 EasyOverlay 备份：缺少备份索引")
            val envelope = gson.fromJson(manifest.readText(Charsets.UTF_8), BackupEnvelope::class.java)
            require(envelope.product == "EasyOverlay" && envelope.formatVersion == 1) { "备份格式不受支持，请更新应用后重试" }
            LibraryValidator.validate(envelope.library)
            require(entries.keys == envelope.library.assets.map { it.relativePath }.toSet()) { "备份图片与索引不一致" }
            val files = envelope.library.assets.associate { asset ->
                val file = entries.getValue(asset.relativePath)
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { inputFile ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) { val count = inputFile.read(buffer); if (count == -1) break; digest.update(buffer, 0, count) }
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                val decoded = images.inspect(file)
                require(hash == asset.sha256 && file.length() == asset.byteCount && decoded.kind.mime == asset.mimeType &&
                    decoded.width == asset.width && decoded.height == asset.height) { "备份图片校验失败，请使用未损坏的备份" }
                asset.id to file
            }
            BackupPreview(envelope.library, files, directory)
        } catch (e: Exception) { directory.deleteRecursively(); throw e }
    }

    suspend fun restore(preview: BackupPreview) {
        try { library.mergeBackup(preview.state, preview.files) } finally { preview.close() }
    }
}
