package com.hycnyl.easyoverlay

import com.hycnyl.easyoverlay.domain.ImagePolicy
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ImagePolicyTest {
    @Test fun animatedWebpFlagIsRejectedBeforeDecoding() {
        val file=File.createTempFile("animated-", ".webp")
        try {
            val bytes=ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".toByteArray()).putInt(22).put("WEBPVP8X".toByteArray()).putInt(10).put(2).put(ByteArray(9)).array()
            file.writeBytes(bytes)
            val result=runCatching {ImagePolicy.detect(file)}
            assertTrue(result.isFailure);assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("动态 WebP"))
        } finally {file.delete()}
    }
    @Test fun excessiveImageAndAnimationDimensionsAreRejected() {
        assertTrue(runCatching {ImagePolicy.validateDimensions(16384,16384,false)}.isFailure)
        assertTrue(runCatching {ImagePolicy.validateDimensions(5000,5000,true)}.isFailure)
        ImagePolicy.validateDimensions(4096,4096,false)
    }
}
