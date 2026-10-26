package com.hycnyl.easyoverlay

import com.hycnyl.easyoverlay.platform.privileged.BinderCallGate
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BinderCallGateTest {
    @Test fun previewWaitsForForegroundQueryWithoutStartingAnotherWorker()=runBlocking {
        val gate=BinderCallGate();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        try {
            val reader=async(Dispatchers.Default){gate.call(3000){entered.countDown();check(release.await(2,TimeUnit.SECONDS));"read"}}
            assertTrue(entered.await(1,TimeUnit.SECONDS))
            val preview=async(Dispatchers.Default){gate.call(3000){"preview"}}
            delay(60);assertFalse(preview.isCompleted);release.countDown()
            assertEquals("read",reader.await());assertEquals("preview",preview.await())
        } finally {release.countDown();gate.close()}
    }
}
