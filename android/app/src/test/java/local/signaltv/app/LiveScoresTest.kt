package local.signaltv.app
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
class LiveScoresTest {
 private val now=1000000L
 private val catalog=InterestCatalog(emptyList(),emptyMap())
 private fun game(id:String,league:String="NFL")=SportsGame(id,league,"Match",now-1000,true,"in","STATUS_IN_PROGRESS","Second quarter",emptyList(),now,false)
 @Test fun delayHoldsScoresAndFinalStatus(){val h=ScoreHistory();val a=game("NFL:1");h.add("NFL",listOf(a),now);assertFalse(h.ready(30,now+29999));h.add("NFL",listOf(a.copy(state="post",lastPlay="Final play")),now+15000);assertEquals("in",h.games(30,now+30000).single().state);assertEquals("post",h.games(30,now+45000).single().state);h.add("NFL",emptyList(),now+60000);assertEquals(1,h.games(30,now+89999).size);assertEquals(0,h.games(30,now+90000).size)}
 @Test fun outageDoesNotEraseLastKnownScore(){val h=ScoreHistory();val g=game("NFL:1");h.add("NFL",listOf(g),now);val old=h.games(0,now+100000).single();assertTrue(scoreAvailable(old,now+100000));assertFalse(sportsLive(old,now+100000));assertEquals(0,h.games(0,now+300001).size);assertEquals("Updated 45s ago",scoreAge(now,now+45000));assertEquals(30,ScorePreferences.parse(ScorePreferences(delay=30,lastPlay=false).json()).delay)}
 @Test fun parseOptionalLastPlay(){val j=JSONObject("""{"events":[{"id":"1","date":"2026-10-18T12:00:00Z","competitions":[{"competitors":[{"team":{"id":"1"}},{"team":{"id":"2"}}],"situation":{"lastPlay":{"text":" Eight yard pass. "},"possession":"2","downDistanceText":"4th & 1"}}]}]}""");val g=sportsGames(SportsPayload(j,now,false),"NFL").single();assertEquals("Eight yard pass.",g.lastPlay);assertEquals("nfl:2",g.possession);assertEquals("4th & 1",g.downDistance)}
 @Test fun onlyFreshLiveGamesAppear(){val g=game("NFL:1");val list=listOf(g,g.copy(id="NFL:2",state="pre"),g.copy(id="NFL:3",status="STATUS_DELAYED"),g.copy(id="NFL:4",stale=true));assertEquals(2,scoreGames(list,ScorePreferences(),"NFL","",catalog,now).size);assertEquals(2,scoreGames(list,ScorePreferences(),"NFL","",catalog,now+91000).size);assertEquals(0,scoreGames(list,ScorePreferences(),"NFL","",catalog,now+300001).size)}
 @Test fun crossSportPinsRetainOrder(){val p=ScorePreferences(mode="pinned",pins=listOf("NBA:2","NFL:1"));assertEquals(p.pins,scoreGames(listOf(game("NFL:1"),game("NBA:2","NBA")),p,"NFL","",catalog,now).map{it.id})}
 @Test fun layoutIsSanitizedAndPersists(){val p=ScorePreferences(visible=true,position="top-right",rows=5,opacity=60,accent="ice",pins=listOf("NFL:1"));assertEquals(p,ScorePreferences.parse(p.json()));val invalid=ScorePreferences.parse(JSONObject().put("rows",0).put("opacity",999).put("position","bad"));assertEquals(3,invalid.rows);assertEquals(80,invalid.opacity);assertEquals("bottom-left",invalid.position);assertEquals(1,scorePageCount(0,3));assertEquals(2,scorePageCount(4,3))}
}
