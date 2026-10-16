package local.signaltv.app
import org.junit.Assert.*
import org.junit.Test
class GuideRemoteTest {
 @Test fun shortPressWatchesOnReleaseOnly(){val p=GuideSelectPress();assertEquals(GuideSelectPress.Action.NONE,p.down(0,0));assertEquals(GuideSelectPress.Action.WATCH,p.up(100));assertEquals(GuideSelectPress.Action.NONE,p.up(100))}
 @Test fun heldPressNeverWatches(){val p=GuideSelectPress();p.down(0,0);assertEquals(GuideSelectPress.Action.MENU,p.down(1,600));assertEquals(GuideSelectPress.Action.NONE,p.down(2,900));assertEquals(GuideSelectPress.Action.NONE,p.up(1000))}
 @Test fun longReleaseWithoutRepeatOpensMenu(){val p=GuideSelectPress();p.down(0,0);assertEquals(GuideSelectPress.Action.MENU,p.up(600))}
 @Test fun cancelledAndDetachedPressDoesNothing(){val p=GuideSelectPress();p.down(0,0);assertEquals(GuideSelectPress.Action.NONE,p.up(50,true));p.down(0,0);p.reset();assertEquals(GuideSelectPress.Action.NONE,p.up(50))}
 @Test fun logoPolicy(){for(s in listOf("http://example.com/a","https://u:p@example.com/a","https://example.com/a?token=x","//example.com/a","https://example.com/a#f",""))assertEquals("",safeChannelLogo(s));assertEquals("https://example.com/a.png",safeChannelLogo("https://example.com/a.png"))}
}
