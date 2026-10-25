package com.hycnyl.easyoverlay

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hycnyl.easyoverlay.data.LibraryRepository
import com.hycnyl.easyoverlay.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],application=Application::class)
class LibraryRepositoryTest {
    private val context: Context get()=ApplicationProvider.getApplicationContext()
    private suspend fun add(repo:LibraryRepository,hash:Char):OverlayAsset {
        val id=UUID.randomUUID().toString()
        val prepared=File.createTempFile("test-",".part",context.cacheDir).apply { writeBytes(byteArrayOf(1)) }
        return repo.install(OverlayAsset(id,"assets/$id.png","image/png",false,1,1,1,hash.toString().repeat(64),1),prepared)
    }
    @Test fun sharedAssetCannotBeDeletedWhileAnyRuleReferencesIt()=runBlocking {
        val repo=LibraryRepository(context); repo.load(); val asset=add(repo,'a')
        repo.saveRule(AppRule("game.one",0,asset.id));repo.saveRule(AppRule("game.two",0,asset.id))
        repo.removeRule("game.one@0")
        assertTrue(runCatching { repo.deleteUnusedAsset(asset.id) }.isFailure)
        assertTrue(repo.assetFile(asset).exists())
        repo.removeRule("game.two@0");repo.deleteUnusedAsset(asset.id)
        assertFalse(repo.assetFile(asset).exists())
    }
    @Test fun concurrentUpdatesRemainPresentAfterProcessReload()=runBlocking {
        val repo=LibraryRepository(context);repo.load();val asset=add(repo,'b')
        coroutineScope { (0..24).map { display -> launch(Dispatchers.Default) { repo.selectQuick(display,asset.id) } }.joinAll() }
        val reloaded=LibraryRepository(context);reloaded.load()
        assertEquals(25,reloaded.state.value.quickSelections.size)
        assertNull(reloaded.problem.value)
    }
    @Test fun corruptIndexIsProtectedAndDoesNotSilentlyBecomeEmpty()=runBlocking {
        val file=File(context.filesDir,"library-v1.json").apply { writeText("{broken") }
        val repo=LibraryRepository(context);repo.load()
        assertNotNull(repo.problem.value)
        assertTrue(runCatching { repo.selectQuick(0,null) }.isFailure)
        assertEquals("{broken",file.readText())
    }
    @Test fun invalidTransactionLeavesPreviouslyCommittedStateAndImageIntact()=runBlocking {
        val repo=LibraryRepository(context);repo.load();val asset=add(repo,'c');repo.selectQuick(0,asset.id)
        assertTrue(runCatching { repo.saveRule(AppRule("game.one",0,"missing")) }.isFailure)
        val reloaded=LibraryRepository(context);reloaded.load()
        assertEquals(asset.id,reloaded.state.value.quickSelections.single().assetId)
        assertTrue(repo.assetFile(asset).exists())
    }
}
