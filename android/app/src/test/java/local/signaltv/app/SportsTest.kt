package local.signaltv.app
import org.junit.Assert.*
import org.junit.Test
class SportsTest{
 @Test fun opponentDoesNotGuessNextGame(){val a=InterestTeam("nfl:22","Arizona Cardinals","NFL","ARI","","",listOf("Cardinals"));val b=a.copy(id="nfl:26",name="Seattle Seahawks",aliases=listOf("Seahawks"));val c=a.copy(id="mlb:24",league="MLB",name="St. Louis Cardinals");val catalog=InterestCatalog(listOf(a,b,c),emptyMap());assertEquals(b,sportsOpponent("Cardinals vs. Seahawks",a,catalog));assertNull(sportsOpponent("Cardinals weekly",a,catalog));assertNull(sportsOpponent("Cardinals vs TBD",a,catalog));assertNull(sportsOpponent("Cardinals and Seahawks news",a,catalog))}
 @Test fun missingAndZeroStatsRemainDistinct(){assertEquals("Stats unavailable",sportsCompact(null));val row=SportsRow("x","X","East","2026","Regular season",listOf(SportsStat("wins","Wins","0"),SportsStat("pointsFor","Points For","21")));assertTrue(sportsCompact(row).contains("W: 0"));assertTrue(sportsCompact(row).contains("For: 21"))}
}
