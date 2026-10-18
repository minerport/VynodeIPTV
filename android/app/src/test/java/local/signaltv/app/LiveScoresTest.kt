package local.signaltv.app
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
class LiveScoresTest {
 private val now=1000000L
 private val catalog=InterestCatalog(emptyList(),emptyMap())
 private fun game(id:String,league:String="NFL")=SportsGame(id,league,"Match",now-1000,true,"in","STATUS_IN_PROGRESS","Second quarter",emptyList(),now,false)
 @Test fun onlyFreshLiveGamesAppear(){val g=game("NFL:1");val list=listOf(g,g.copy(id="NFL:2",state="pre"),g.copy(id="NFL:3",status="STATUS_DELAYED"),g.copy(id="NFL:4",stale=true));assertEquals(1,scoreGames(list,ScorePreferences(),"NFL","",catalog,now).size);assertEquals(0,scoreGames(list,ScorePreferences(),"NFL","",catalog,now+91000).size)}
 @Test fun crossSportPinsRetainOrder(){val p=ScorePreferences(mode="pinned",pins=listOf("NBA:2","NFL:1"));assertEquals(p.pins,scoreGames(listOf(game("NFL:1"),game("NBA:2","NBA")),p,"NFL","",catalog,now).map{it.id})}
 @Test fun layoutIsSanitizedAndPersists(){val p=ScorePreferences(visible=true,position="top-right",rows=5,opacity=60,accent="ice",pins=listOf("NFL:1"));assertEquals(p,ScorePreferences.parse(p.json()));val invalid=ScorePreferences.parse(JSONObject().put("rows",0).put("opacity",999).put("position","bad"));assertEquals(3,invalid.rows);assertEquals(80,invalid.opacity);assertEquals("bottom-left",invalid.position);assertEquals(1,scorePageCount(0,3));assertEquals(2,scorePageCount(4,3))}
}
