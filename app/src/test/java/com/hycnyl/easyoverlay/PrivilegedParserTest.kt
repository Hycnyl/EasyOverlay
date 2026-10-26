package com.hycnyl.easyoverlay

import com.hycnyl.easyoverlay.platform.privileged.PrivilegedSnapshotParsers
import org.junit.Assert.*
import org.junit.Test

class PrivilegedParserTest {
    @Test fun packageAndUidAppOpsRemainSeparateForExactRestore() {
        val modes=PrivilegedSnapshotParsers.usageModes("""
            Uid mode: GET_USAGE_STATS: ignore
              GET_USAGE_STATS: allow; time=+5m
        """.trimIndent())
        assertEquals("allow",modes.packageMode);assertEquals("ignore",modes.uidMode)
        assertEquals("default",PrivilegedSnapshotParsers.usageModes("No operations.").packageMode)
        assertTrue(runCatching {PrivilegedSnapshotParsers.usageModes("OEM unrecognized output")}.isFailure)
    }
    @Test fun batteryRestoreDistinguishesUserAndSystemWhitelistAndExactPackage() {
        val text="system,com.hycnyl.easyoverlay,10001\nuser,com.hycnyl.easyoverlay.other,10002"
        assertEquals(false to true,PrivilegedSnapshotParsers.batteryWhitelist(text,"com.hycnyl.easyoverlay"))
        assertEquals(true to true,PrivilegedSnapshotParsers.batteryWhitelist(text+"\nuser,com.hycnyl.easyoverlay,10001","com.hycnyl.easyoverlay"))
    }
    @Test fun dumpsysDoesNotMistakeSecondaryResumeForPrimary() {
        val text="""
            Display #0 (activities from top to bottom):
              topResumedActivity=ActivityRecord{abc u0 game.main/.Play t2}
            Display #7 (activities from top to bottom):
              mResumedActivity: ActivityRecord{def u0 game.second/.Play t3}
        """.trimIndent()
        assertEquals("game.main/.Play",PrivilegedSnapshotParsers.defaultDisplayComponent(text))
        assertNull(PrivilegedSnapshotParsers.defaultDisplayComponent("Display #7\n mResumedActivity: game.second/.Play"))
        assertNull(PrivilegedSnapshotParsers.defaultDisplayComponent("mResumedActivity: game.a/.A\nmResumedActivity: game.b/.B"))
    }
}
