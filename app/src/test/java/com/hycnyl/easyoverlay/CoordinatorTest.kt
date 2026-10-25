package com.hycnyl.easyoverlay

import com.hycnyl.easyoverlay.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoordinatorTest {
    private class Image : PreparedOverlay { var closed=false; override fun close() { closed=true } }
    private class Engine : OverlayEngine {
        val actual=mutableMapOf<Int,OverlayTarget>()
        var failAsset: String?=null
        var delayMs=0L
        override suspend fun prepare(asset: OverlayAsset, displayId: Int): PreparedOverlay {
            delay(delayMs); if (asset.id==failAsset) error("broken image"); return Image()
        }
        override fun mount(target: OverlayTarget,image: PreparedOverlay) { actual[target.displayId]=target }
        override fun remove(displayId: Int) { actual.remove(displayId) }
        override fun targets()=actual.toMap()
        override fun resize() {}
        override fun close() { actual.clear() }
    }
    private fun asset(id:String)=OverlayAsset(id,"assets/$id.png","image/png",false,1,1,0,"0".repeat(64),1)
    private fun TestScope.fixture(): Triple<OverlayCoordinator,MutableStateFlow<LibraryState>,Pair<Engine,MutableStateFlow<RuntimeState>>> {
        val library=MutableStateFlow(LibraryState(assets=listOf(asset("A"),asset("B"),asset("manual")),
            rules=listOf(AppRule("game.a",0,"A"),AppRule("game.b",0,"B")),
            quickSelections=listOf(QuickSelection(0,"manual"),QuickSelection(8,"manual"))))
        val engine=Engine(); val runtime=MutableStateFlow(RuntimeState())
        val coordinator=OverlayCoordinator(this,library,engine,runtime,"self.app",{0.8f},{true},{true},{},{it},{_,_->},{})
        return Triple(coordinator,library,engine to runtime)
    }
    @Test fun stateOnlyClaimsMountAfterPreparationFinishes()=runTest {
        val (co,_,pair)=fixture(); val (engine,state)=pair
        try {
            engine.delayMs=100
            co.offer(OverlayCommand.StartQuick(0)); runCurrent()
            assertTrue(state.value.sessions.isEmpty()); assertEquals(setOf(0),state.value.pendingDisplays)
            advanceTimeBy(100); runCurrent()
            assertEquals(OverlayOwner.QUICK_USE,state.value.sessions[0]?.target?.owner)
        } finally { co.close() }
    }
    @Test fun quickPriorityAndStopResumeLatestB()=runTest {
        val (co,_,pair)=fixture(); val (_,state)=pair
        try {
            co.offer(OverlayCommand.SetAuto(true)); co.offer(OverlayCommand.Observe("game.a",1,null)); advanceUntilIdle()
            assertEquals("A",state.value.sessions[0]?.target?.assetId)
            co.offer(OverlayCommand.StartQuick(0)); advanceUntilIdle()
            co.offer(OverlayCommand.Observe("game.b",2,null)); advanceUntilIdle()
            assertEquals("manual",state.value.sessions[0]?.target?.assetId)
            co.offer(OverlayCommand.StopQuick(0)); advanceUntilIdle()
            assertEquals("B",state.value.sessions[0]?.target?.assetId)
        } finally { co.close() }
    }
    @Test fun unknownForegroundDoesNotRestoreAAndDisableKeepsOtherScreenManual()=runTest {
        val (co,_,pair)=fixture(); val (_,state)=pair
        try {
            co.offer(OverlayCommand.Displays(setOf(0,8))); co.offer(OverlayCommand.StartQuick(8))
            co.offer(OverlayCommand.SetAuto(true)); co.offer(OverlayCommand.Observe("game.a",1,null)); advanceUntilIdle()
            co.offer(OverlayCommand.StartQuick(0)); co.offer(OverlayCommand.Observe("game.c",2,null)); advanceUntilIdle()
            co.offer(OverlayCommand.StopQuick(0)); advanceUntilIdle()
            assertEquals(setOf(8),state.value.sessions.keys)
            co.offer(OverlayCommand.SetAuto(false)); advanceUntilIdle()
            assertEquals(OverlayOwner.QUICK_USE,state.value.sessions[8]?.target?.owner)
        } finally { co.close() }
    }
    @Test fun failedReplacementKeepsMountedImageAndStaleCompletionIsReleased()=runTest {
        val (co,library,pair)=fixture(); val (engine,state)=pair
        try {
            co.offer(OverlayCommand.StartQuick(0)); advanceUntilIdle()
            engine.failAsset="B"
            library.value=library.value.copy(quickSelections=listOf(QuickSelection(0,"B")))
            co.offer(OverlayCommand.Refresh()); advanceUntilIdle()
            assertEquals("manual",state.value.sessions[0]?.target?.assetId)
            assertTrue(state.value.failures.containsKey(0))
            val obsolete=Image()
            co.offer(OverlayCommand.Prepared(OverlayTarget(0,OverlayOwner.QUICK_USE,"A",60),0,obsolete)); advanceUntilIdle()
            assertTrue(obsolete.closed); assertEquals("manual",state.value.sessions[0]?.target?.assetId)
        } finally { co.close() }
    }
    @Test fun usageRevocationRemovesOnlyRuleOwnedWindow()=runTest {
        val (co,_,pair)=fixture(); val (_,state)=pair
        try {
            co.offer(OverlayCommand.Displays(setOf(0,8))); co.offer(OverlayCommand.StartQuick(8))
            co.offer(OverlayCommand.SetAuto(true)); co.offer(OverlayCommand.Observe("game.a",1,null)); advanceUntilIdle()
            co.offer(OverlayCommand.Observe(null,2,"USAGE_PERMISSION_MISSING")); advanceUntilIdle()
            assertEquals(setOf(8),state.value.sessions.keys)
            assertEquals("USAGE_PERMISSION_MISSING",state.value.monitorProblem)
        } finally { co.close() }
    }
}
