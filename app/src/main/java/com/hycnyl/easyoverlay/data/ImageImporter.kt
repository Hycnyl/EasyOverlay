package com.hycnyl.easyoverlay.data

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import com.hycnyl.easyoverlay.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.max

class ImageImporter(private val context: Context, private val library: LibraryRepository) {
    suspend fun import(uri: Uri): OverlayAsset = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "请通过系统文件选择器授权读取图片" }
        val input = try { context.contentResolver.openInputStream(uri) } catch (_: SecurityException) {
            error("图片读取授权已失效，请重新选择图片")
        } ?: error("无法打开图片，请确认文件仍然存在")
        input.use { importStream(it) }
    }

    suspend fun importStream(input: InputStream): OverlayAsset = withContext(Dispatchers.IO) {
        val temporary = File.createTempFile("import-", ".part", context.cacheDir)
        try {
            val hash = copyBounded(input, temporary)
            val info = inspect(temporary)
            val id = UUID.randomUUID().toString()
            library.install(OverlayAsset(id, "assets/$id.${info.kind.extension}", info.kind.mime,
                info.kind == ImageKind.GIF, info.width, info.height, System.currentTimeMillis(), hash, temporary.length()), temporary)
        } finally { temporary.delete() }
    }

    data class Inspection(val kind: ImageKind, val width: Int, val height: Int)

    fun inspect(file: File): Inspection {
        val kind = ImagePolicy.detect(file)
        var width = 0
        var height = 0
        val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
            width = info.size.width; height = info.size.height
            require(info.mimeType == kind.mime) { "图片内容与文件格式不符，请重新导出图片" }
            ImagePolicy.validateDimensions(width, height, kind == ImageKind.GIF)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSampleSize(max(1, max(width, height) / 512))
            decoder.setOnPartialImageListener { false }
        }
        (drawable as? AnimatedImageDrawable)?.stop()
        return Inspection(kind, width, height)
    }

    suspend fun copyBounded(input: InputStream, destination: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileOutputStream(destination).use { output ->
            val buffer = ByteArray(32 * 1024)
            var total = 0L
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count == -1) break
                total += count
                require(total <= ImagePolicy.MAX_BYTES) { "图片超过 32 MB，请先压缩或缩小图片" }
                digest.update(buffer, 0, count)
                output.write(buffer, 0, count)
            }
            output.fd.sync()
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

object AssetDecoder {
    fun decode(file: File, width: Int, height: Int): Drawable =
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
            ImagePolicy.validateDimensions(info.size.width, info.size.height, info.isAnimated)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            // Cap decoded surfaces at 2048 px per side; overlays are scaled by ImageView.
            val targetWidth = width.coerceIn(1, 2048)
            val targetHeight = height.coerceIn(1, 2048)
            val ratio = max(info.size.width.toFloat() / targetWidth, info.size.height.toFloat() / targetHeight)
            if (ratio > 1) decoder.setTargetSize(max(1, (info.size.width / ratio).toInt()), max(1, (info.size.height / ratio).toInt()))
            decoder.setOnPartialImageListener { false }
        }
}
