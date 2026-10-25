package com.hycnyl.easyoverlay

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.hycnyl.easyoverlay.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],application=Application::class)
class BackupSafetyTest {
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    @Test fun zipTraversalIsRejectedAndWritesNothingOutsideStaging()=runBlocking {
        val repo=LibraryRepository(context);repo.load();val manager=BackupManager(context,repo,ImageImporter(context,repo))
        val zip=File(context.cacheDir,"malicious.zip")
        ZipOutputStream(zip.outputStream()).use {it.putNextEntry(ZipEntry("../escaped.txt"));it.write("bad".toByteArray());it.closeEntry()}
        assertTrue(runCatching {manager.preview(Uri.fromFile(zip))}.isFailure)
        assertFalse(File(context.cacheDir,"escaped.txt").exists())
        assertTrue(repo.state.value.assets.isEmpty())
    }
    @Test fun unknownBackupVersionCannotChangeLibrary()=runBlocking {
        val repo=LibraryRepository(context);repo.load();val manager=BackupManager(context,repo,ImageImporter(context,repo))
        val zip=File(context.cacheDir,"future.zip")
        ZipOutputStream(zip.outputStream()).use {it.putNextEntry(ZipEntry("EasyOverlay-backup.json"));it.write("""{"product":"EasyOverlay","formatVersion":99,"library":{}}""".toByteArray());it.closeEntry()}
        assertTrue(runCatching {manager.preview(Uri.fromFile(zip))}.isFailure)
        assertTrue(repo.state.value.assets.isEmpty())
    }
}
