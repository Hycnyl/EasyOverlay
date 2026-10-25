package com.hycnyl.easyoverlay

import com.hycnyl.easyoverlay.domain.LegacyPlanner
import org.junit.Assert.*
import org.junit.Test

class LegacyPlannerTest {
    @Test fun preferredConfigWinsWithoutDeletingOtherAssets() {
        val plan = LegacyPlanner.parse("""[{"groupName":"game","boundPackageName":"game.one","defaultConfigName":"chosen","configs":[{"configName":"other","imageUri":"other.png"},{"configName":"chosen","imageUri":"chosen.png"}]}]""")
        assertEquals(2, plan.items.size)
        assertEquals(plan.items.single { it.configName == "chosen" }.key, plan.candidates.single())
    }
    @Test fun duplicateDefaultsAndUnboundGroupsRemainUnassigned() {
        val plan=LegacyPlanner.parse("""[{"groupName":"a","boundPackageName":"game.one","configs":[{"configName":"x","imageUri":"x.png","isDefault":true},{"configName":"y","imageUri":"y.png","isDefault":true}]},{"groupName":"free","configs":[{"configName":"z","imageUri":"z.gif"}]}]""")
        assertEquals(3,plan.items.size)
        assertTrue(plan.candidates.isEmpty())
        assertNull(plan.items.last().packageName)
    }
    @Test fun rerunProducesStableKeysAndAbsolutePathsOnlyBecomeTreeRelative() {
        val json="""[{"id":"stable","groupName":"G","boundPackageName":"game.one","configs":[{"configName":"a","imageUri":"file:///storage/emulated/0/Stuff/ImageOverlay/G/a.png"}]}]"""
        assertEquals(LegacyPlanner.parse(json),LegacyPlanner.parse(json))
        val paths=LegacyPlanner.relativeCandidates("file:///storage/emulated/0/Stuff/ImageOverlay/G/a.png","G","a")
        assertEquals("G/a.png",paths.first())
        assertTrue(paths.none { it.startsWith("/") })
        assertTrue(LegacyPlanner.relativeCandidates("../../secret","G","a").isEmpty())
    }
}
