package local.signaltv.app
import org.junit.Assert.*
import org.junit.Test
class InterestsTest {
 private val team=InterestTeam("nfl:22","Arizona Cardinals","NFL","ARI","a40227","",listOf("Arizona Cardinals","Cardinals"))
 private val catalog=InterestCatalog(listOf(team),mapOf("Comedy" to listOf("comedy","sitcom")))
 private val c=Channel("c","ABC Austin TX",groupId="p:g")
 private val prefs=InterestSettings(listOf("nfl:22"),"Austin","TX",listOf("Comedy"))
 private fun matches(title:String,genres:String="")=matchInterests(prefs,catalog,listOf(c),mapOf("c" to listOf(Programme(title,0,2000,genres=genres))),emptySet(),1000)
 @Test fun teamAmbiguityAndWordBoundaries(){assertTrue(matches("NFL Football: Cardinals vs Seahawks").any{it.section=="sports"});assertFalse(matches("MLB Baseball: Cardinals vs Cubs").any{it.section=="sports"});assertFalse(matches("Cardinals of Rome").any{it.section=="sports"});assertFalse(interestHas(interestWords("Austinville TX"),"Austin"))}
 @Test fun genreCategoryBeatsIncidentalDescription(){assertTrue(matches("A special","Comedy").any{it.section=="genres"});assertFalse(matches("Comedy in a dramatic life","Drama").any{it.section=="genres"})}
 @Test fun offsetSchedulesMatchUtc(){assertEquals(programmeEpoch("2026-10-18T17:21:58.0719776Z"),programmeEpoch("2026-10-18T13:21:58.0719776-04:00"))}
 @Test fun stateAbbreviations(){assertTrue(interestState(interestWords("ABC Austin TX"),"Texas",mapOf("TX" to "Texas")));assertFalse(interestState(interestWords("ABC Austin TX"),"California",mapOf("TX" to "Texas","CA" to "California")))}
 @Test fun localHiddenAndExpired(){val guide=mapOf("c" to listOf(Programme("News",0,2000)));assertEquals(1,matchInterests(prefs,catalog,listOf(c),guide,emptySet(),1000).size);assertTrue(matchInterests(prefs,catalog,listOf(c),guide,setOf("provider:p:g"),1000).isEmpty());assertTrue(matchInterests(prefs,catalog,listOf(c),guide,emptySet(),2000).isEmpty());assertTrue(matchInterests(prefs.copy(state="CA"),catalog,listOf(c),guide,emptySet(),1000).isEmpty());assertEquals(1,matchInterests(prefs.copy(state="CA",localChannels=listOf("c")),catalog,listOf(c),guide,emptySet(),1000).size)}
}
