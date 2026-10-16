package local.signaltv.app
import org.junit.Assert.*
import org.junit.Test
class GuideSearchTest{
 @Test fun searchesAllChannelsAndFutureEpisodesWithoutOldListings(){
  val c=Channel("1","Cinema",groupName="Movies")
  val programmes=mapOf("1" to listOf(Programme("Ocean Adventure",900,2000,episodeTitle="The Island"),Programme("Ocean Adventure",100,900)))
  val hits=searchGuide("ISLAND ocean",listOf(c),programmes,1000)
  assertEquals(1,hits.size);assertEquals("1",hits.first().channel?.id)
  assertEquals(1,searchGuide("movies",listOf(c),programmes,1000).size)
  assertTrue(searchGuide("  ",listOf(c),programmes,1000).isEmpty())
 }
 @Test fun boundedResults(){assertEquals(100,searchGuide("tv",(1..120).map{Channel("$it","TV $it")},emptyMap(),0).size)}
}
