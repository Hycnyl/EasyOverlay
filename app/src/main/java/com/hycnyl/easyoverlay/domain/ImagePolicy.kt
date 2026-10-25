package com.hycnyl.easyoverlay.domain

import java.io.File
import java.io.RandomAccessFile

enum class ImageKind(val mime: String, val extension: String) {
    PNG("image/png", "png"), JPEG("image/jpeg", "jpg"), WEBP("image/webp", "webp"), GIF("image/gif", "gif")
}

object ImagePolicy {
    const val MAX_BYTES = 32L * 1024 * 1024
    fun detect(file: File): ImageKind {
        require(file.length() in 12..MAX_BYTES) { "图片为空、损坏或超过 32 MB，请选择较小图片" }
        val header = file.inputStream().use { it.readNBytes(12) }
        val kind = when {
            header.take(8).toByteArray().contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) -> ImageKind.PNG
            header[0] == 0xff.toByte() && header[1] == 0xd8.toByte() && header[2] == 0xff.toByte() -> ImageKind.JPEG
            String(header, 0, 6, Charsets.US_ASCII) in setOf("GIF87a", "GIF89a") -> ImageKind.GIF
            String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "WEBP" -> ImageKind.WEBP
            else -> error("不支持这种图片。请选择 PNG、JPG/JPEG、静态 WebP 或 GIF；暂不支持 SVG。")
        }
        if (kind == ImageKind.WEBP) rejectAnimatedWebP(file)
        return kind
    }

    private fun rejectAnimatedWebP(file: File) = RandomAccessFile(file, "r").use { input ->
        input.seek(4)
        val declaredSize = readUIntLE(input) + 8
        require(declaredSize == input.length()) { "WebP 文件不完整或格式损坏" }
        var cursor = 12L
        while (cursor < declaredSize) {
            require(cursor + 8 <= declaredSize) { "WebP 数据块损坏" }
            input.seek(cursor)
            val chunk = ByteArray(4).also { input.readFully(it) }.toString(Charsets.US_ASCII)
            val size = readUIntLE(input)
            require(size <= declaredSize - cursor - 8) { "WebP 数据块损坏" }
            require(chunk != "ANIM" && chunk != "ANMF") { "暂不支持动态 WebP，请转换为 GIF 或静态 WebP" }
            if (chunk == "VP8X") {
                require(size >= 10) { "WebP 扩展头损坏" }
                require(input.readUnsignedByte() and 0x02 == 0) { "暂不支持动态 WebP，请转换为 GIF" }
            }
            cursor += 8 + size + size % 2
        }
        require(cursor == declaredSize) { "WebP 数据块长度无效" }
    }
    private fun readUIntLE(input: RandomAccessFile): Long = (0 until 4).fold(0L) { result, shift ->
        result or (input.readUnsignedByte().toLong() shl (shift * 8))
    }

    fun validateDimensions(width: Int, height: Int, animated: Boolean) {
        val pixels = width.toLong() * height
        require(width in 1..16384 && height in 1..16384 && pixels <= if (animated) 16_000_000 else 64_000_000) {
            "图片尺寸过大，请缩小到 16384 像素以内；静态图片最多 6400 万像素，GIF 最多 1600 万像素"
        }
    }
}
