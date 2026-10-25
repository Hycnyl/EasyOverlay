package com.hycnyl.easyoverlay

import com.hycnyl.easyoverlay.domain.*
import org.junit.Assert.*
import org.junit.Test

class ForegroundReducerTest {
    private fun event(t: Long, pkg: String, kind: ForegroundKind = ForegroundKind.RESUMED, activity: String = "Main") =
        ForegroundEvent(t, pkg, activity, kind)
    @Test fun latePauseForADoesNotCloseBAndOverlapIsIdempotent() {
        val reducer = ForegroundReducer()
        val events = listOf(event(1000,"game.a"), event(1100,"game.b"), event(1150,"game.a",ForegroundKind.PAUSED))
        assertEquals("game.b", reducer.accept(events, 1200))
        assertEquals("game.b", reducer.accept(events, 1800))
        assertEquals("game.b", reducer.accept(listOf(event(1050,"game.c")), 1900))
    }
    @Test fun rapidABSwitchDoesNotHaveGlobalCooldown() {
        val reducer = ForegroundReducer()
        for (i in 1L..20L) {
            val pkg = if (i % 2L == 0L) "game.b" else "game.a"
            assertEquals(pkg, reducer.accept(listOf(event(1000+i*40,pkg)), 1000+i*40))
        }
    }
    @Test fun multipleActivitiesDoNotBlankNewActivityAndRealPauseExpires() {
        val reducer = ForegroundReducer()
        reducer.accept(listOf(event(1000,"game.a",activity="One")),1000)
        reducer.accept(listOf(event(1100,"game.a",activity="Two"),event(1120,"game.a",ForegroundKind.PAUSED,"One")),1120)
        assertEquals("game.a",reducer.accept(emptyList(),1600))
        reducer.accept(listOf(event(1700,"game.a",ForegroundKind.PAUSED,"Two")),1700)
        assertEquals("game.a",reducer.accept(emptyList(),1800))
        assertNull(reducer.accept(emptyList(),2100))
    }
    @Test fun resetAndScreenOffForgetHistoricalTarget() {
        val reducer=ForegroundReducer()
        reducer.accept(listOf(event(1000,"game.a")),1000)
        assertNull(reducer.accept(listOf(event(1100,"",ForegroundKind.SCREEN_OFF)),1100))
        reducer.reset()
        assertNull(reducer.accept(emptyList(),1200))
    }
}
