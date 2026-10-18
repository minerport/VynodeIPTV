package local.signaltv.app
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class SportsPlayersTest{
 @Test fun columnsAreAlignedAndMissingIsNotZero(){val t=sportsPlayers(JSONObject("""{"pagination":{"page":2,"pages":3,"count":140},"categories":[{"name":"passing","names":["yards","td"],"labels":["YDS","TD"]}],"athletes":[{"athlete":{"id":"1","displayName":"A"},"categories":[{"name":"passing","totals":["0","—"],"values":[0,null]}]}]}"""));assertEquals(2,t.page);assertEquals(3,t.pages);assertEquals(0.0,t.players.first().stats.first().numeric!!,0.0);assertNull(t.players.first().stats[1].numeric);assertEquals("passing.td",sportsPlayerColumns(t,"passing.td").first().key)}
 @Test fun teamUsesRosterEndpoint(){val address=sportsPlayerAddress("players","NFL","nfl:2","2026","passing.passingYards:desc",1,"2");assertTrue(address.contains("/teams/2/athletes/statistics"));assertTrue(address.contains("seasontype=2"))}
 @Test fun unsafeQueryRejected(){for(sort in listOf("http://evil","yards&url=x","../../x")){assertThrows(IllegalArgumentException::class.java){sportsPlayerAddress("players","NFL","","",sort,1,"")}};assertThrows(IllegalArgumentException::class.java){sportsPlayerAddress("players","NFL","","","",101,"")}}
 @Test fun emptyIsNotFabricated(){assertTrue(sportsPlayers(JSONObject()).players.isEmpty())}
}
