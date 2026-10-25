package com.hycnyl.easyoverlay

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.hycnyl.easyoverlay.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageImportTest {
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private fun bytes(format:Bitmap.CompressFormat):ByteArray {
        val bitmap=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff338855.toInt());bitmap.setPixel(0,0,0x00338855)
        return ByteArrayOutputStream().use {output->assertTrue(bitmap.compress(format,95,output));bitmap.recycle();output.toByteArray()}
    }
    @Test fun pngAlphaIsPreservedAndRepeatedImportReusesAsset()=runBlocking {
        val repo=LibraryRepository(context);repo.load();val images=ImageImporter(context,repo);val source=bytes(Bitmap.CompressFormat.PNG)
        val first=images.importStream(source.inputStream());val second=images.importStream(source.inputStream())
        assertEquals(first.id,second.id);assertEquals(1,repo.state.value.assets.size)
        val decoded=BitmapFactory.decodeFile(repo.assetFile(first).path)
        assertEquals(0,decoded.getPixel(0,0) ushr 24)
    }
    @Test fun jpegAndStaticWebpDecodeUsingActualBytes()=runBlocking {
        val repo=LibraryRepository(context);repo.load();val images=ImageImporter(context,repo)
        assertEquals("image/jpeg",images.importStream(bytes(Bitmap.CompressFormat.JPEG).inputStream()).mimeType)
        assertEquals("image/webp",images.importStream(bytes(Bitmap.CompressFormat.WEBP_LOSSLESS).inputStream()).mimeType)
    }
    @Test fun gifRemainsGifRatherThanReencodingToPng()=runBlocking {
        val repo=LibraryRepository(context);repo.load();val images=ImageImporter(context,repo)
        val source=Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7")
        val asset=images.importStream(source.inputStream())
        assertEquals("image/gif",asset.mimeType);assertTrue(asset.isAnimatedGif)
        assertArrayEquals(source,repo.assetFile(asset).readBytes())
    }
    @Test fun brokenImageDoesNotCreateAssetOrOverwriteCurrentSelection()=runBlocking {
        val repo=LibraryRepository(context);repo.load();val images=ImageImporter(context,repo)
        val asset=images.importStream(bytes(Bitmap.CompressFormat.PNG).inputStream());repo.selectQuick(0,asset.id)
        val corrupt=byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a)+ByteArray(30)
        assertTrue(runCatching {images.importStream(corrupt.inputStream())}.isFailure)
        assertEquals(asset.id,repo.state.value.quickSelections.single().assetId)
        assertEquals(1,repo.state.value.assets.size)
    }
}
