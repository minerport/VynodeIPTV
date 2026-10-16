package local.signaltv.app
import org.junit.Test
import org.junit.Assert.*
class DiscoverTest {
 @Test fun candidatesBoundedAndDeduplicated(){val channels=(0..30).map{Channel(it.toString(),"Channel $it")};val result=discoverChannels(channels,setOf("4","missing"),listOf("1","1"));assertEquals(12,result.size);assertEquals(result.size,result.map{it.id}.toSet().size);assertTrue(result.any{it.id=="4"});assertFalse(result.any{it.id=="missing"})}
 @Test fun launchChoiceIsIndependentOfLayout(){val s=AppearanceSettings(layout="side",startup="home").normalized();assertEquals("side",s.layout);assertEquals("home",s.startup);assertEquals("guide",s.copy(startup="bad").normalized().startup)}
}
