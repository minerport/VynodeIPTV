package local.signaltv.app

import org.junit.Assert.*
import org.junit.Test

class WallModeTest {
    @Test fun clearedWallStaysSingleWhenNextChannelIsSavedAndRestored(){
        val cleared=WallMode().enter().exit()
        assertFalse(cleared.enabled)
        assertFalse(WallMode.restore(cleared.enabled,1).enabled)
    }
    @Test fun legacySingleChannelDoesNotReactivatePanels(){
        assertFalse(WallMode.restore(null,1).enabled)
        assertTrue(WallMode.restore(null,4).enabled)
    }
    @Test fun explicitOnePanelWallIsPreservedButAnEmptyWallExits(){
        assertTrue(WallMode.restore(true,1).enabled)
        assertFalse(WallMode.restore(true,0).enabled)
        assertFalse(WallMode.restore(false,4).enabled)
    }
}
