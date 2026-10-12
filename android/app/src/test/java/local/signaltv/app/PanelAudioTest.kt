package local.signaltv.app

import org.junit.Assert.*
import org.junit.Test

class PanelAudioTest {
    private class Panel(var volume:Float=0f)
    @Test fun movingFocusTransfersAudioWithoutOverlappingOwners(){
        val panels=(0..5).associateWith{Panel()}
        listOf(0,5,2,1,0).forEach{selected->
            routePanelAudio(panels,selected){panel,volume->
                panel.volume=volume
                assertTrue(panels.values.count{it.volume>0}<=1)
            }
            assertEquals(setOf(selected),panels.filterValues{it.volume>0}.keys)
        }
    }
    @Test fun lateBackgroundPanelDoesNotStealAudio(){
        val panels=mutableMapOf(0 to Panel());routePanelAudio(panels,0){p,v->p.volume=v}
        panels[3]=Panel();routePanelAudio(panels,0){p,v->p.volume=v}
        assertEquals(1f,panels.getValue(0).volume);assertEquals(0f,panels.getValue(3).volume)
    }
    @Test fun emptyPanelFocusMutesOldChannelUntilSelectedPanelStarts(){
        val panels=mutableMapOf(0 to Panel(1f));routePanelAudio(panels,2){p,v->p.volume=v}
        assertEquals(0f,panels.getValue(0).volume)
        panels[2]=Panel();routePanelAudio(panels,2){p,v->p.volume=v}
        assertEquals(1f,panels.getValue(2).volume)
    }
}
