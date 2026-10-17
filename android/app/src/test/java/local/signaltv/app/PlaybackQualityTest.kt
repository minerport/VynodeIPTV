package local.signaltv.app
import org.junit.Assert.assertEquals
import org.junit.Test
class PlaybackQualityTest {
 @Test fun companionUsesNativeCodecsUnlessConversionRequested(){assertEquals("remux",companionQuality("auto"));assertEquals("remux",companionQuality("original"));assertEquals("data-saver",companionQuality("data-saver"));assertEquals("remux",companionQuality("invalid"))}
}
