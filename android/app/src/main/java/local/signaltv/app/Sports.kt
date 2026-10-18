package local.signaltv.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import android.content.Context

data class SportsStat(val key:String,val label:String,val value:String)
data class SportsRow(val id:String,val name:String,val group:String,val season:String,val phase:String,val stats:List<SportsStat>)
data class SportsLeague(val league:String,val rows:List<SportsRow>,val updated:Long,val stale:Boolean=false)
val sportsPaths=mapOf("NFL" to "football/nfl","NBA" to "basketball/nba","MLB" to "baseball/mlb","NHL" to "hockey/nhl","MLS" to "soccer/usa.1")
private val sportsFields=setOf("overall","wins","losses","ties","otLosses","points","winPercent","playoffSeed","rank","streak","pointsFor","pointsAgainst","pointDifferential","avgPointsFor","avgPointsAgainst","Home","Road","Last Ten Games")
fun parseStandings(root:JSONObject,league:String):List<SportsRow>{
 val rows=mutableListOf<SportsRow>()
 fun walk(n:JSONObject){val s=n.optJSONObject("standings");val entries=s?.optJSONArray("entries")
 if(entries!=null)for(i in 0 until entries.length()){val e=entries.getJSONObject(i);val t=e.getJSONObject("team");val a=e.optJSONArray("stats");val stats=if(a==null)emptyList()else (0 until a.length()).map{a.getJSONObject(it)}.filter{it.optString("name") in sportsFields&&it.has("displayValue")&&(it.optString("name")!="points"||league in setOf("NHL","MLS"))}.map{SportsStat(it.getString("name"),when(it.getString("name")){"overall"->"Record";"pointsFor"->if(league=="MLB")"Runs scored" else if(league in setOf("NHL","MLS"))"Goals for" else "Points for";"pointsAgainst"->if(league=="MLB")"Runs allowed" else if(league in setOf("NHL","MLS"))"Goals against" else "Points against";else->it.optString("displayName",it.getString("name"))},it.getString("displayValue"))}
 rows.add(SportsRow(league.lowercase(java.util.Locale.ROOT)+":"+t.getString("id"),t.optString("displayName"),n.optString("name"),s.optString("seasonDisplayName",s.optString("season")),mapOf(1 to "Preseason",2 to "Regular season",3 to "Postseason")[s.optInt("seasonType")]?:"Season",stats))}
 val children=n.optJSONArray("children");if(children!=null)for(i in 0 until children.length())walk(children.getJSONObject(i))
 };walk(root);return rows
}
fun sportsOpponent(title:String,team:InterestTeam,catalog:InterestCatalog):InterestTeam?{
 if(!Regex("(?:\\bvs?\\.?\\s|\\bat\\b|@| versus )",RegexOption.IGNORE_CASE).containsMatchIn(title))return null
 val text=interestWords(title);val found=catalog.teams.filter{it.league==team.league&&(listOf(it.name)+it.aliases).any{a->interestHas(text,a)}}
 return if(found.size==2&&found.any{it.id==team.id})found.firstOrNull{it.id!=team.id}else null
}
fun sportsCompact(row:SportsRow?):String{
 if(row==null)return "Stats unavailable"
 val labels=mapOf("wins" to "W","losses" to "L","ties" to "T","otLosses" to "OTL","pointsFor" to "For","pointsAgainst" to "Against","pointDifferential" to "Diff","streak" to "Streak","overall" to "Record")
 return row.stats.filter{it.key in labels&&(!row.stats.any{s->s.key=="overall"}||it.key !in setOf("wins","losses","ties","otLosses"))}.joinToString(" · "){labels[it.key]+": "+it.value}.ifBlank{"Stats unavailable"}
}
class SportsStore(context:Context,private val profile:Profile){
 private val prefs=context.getSharedPreferences("sports-public-cache",Context.MODE_PRIVATE)
 suspend fun load(league:String):SportsLeague=withContext(Dispatchers.IO){
 val path=requireNotNull(sportsPaths[league]);val now=System.currentTimeMillis();val key=if(profile.mode=="server")"server:"+profile.base+":"+league else league
 val cached=runCatching{JSONObject(prefs.getString(key,"")!!)}.getOrNull();val stamp=cached?.optLong("saved")?:0
 if(cached!=null&&now-stamp in 0..900000&&now-cached.optLong("updated") in 0..86400000)return@withContext SportsLeague(league,parseStandings(cached.getJSONObject("data"),league),cached.optLong("updated"),cached.optBoolean("stale"))
 try{val payload=if(profile.mode=="server")JSONObject(request(profile.base+"/api/v1/sports/standings?league="+league,profile.token))else JSONObject().put("data",JSONObject(request("https://site.api.espn.com/apis/v2/sports/"+path+"/standings")))
 val data=payload.getJSONObject("data");val rows=parseStandings(data,league);require(rows.isNotEmpty());val updated=if(payload.has("updatedAt"))programmeEpoch(payload.getString("updatedAt"))else now;val stale=payload.optBoolean("stale")
 prefs.edit().putString(key,JSONObject().put("data",data).put("saved",now).put("updated",updated).put("stale",stale).toString()).apply();SportsLeague(league,rows,updated,stale)
 }catch(e:CancellationException){throw e}catch(e:Exception){if(cached!=null&&now-cached.optLong("updated") in 0..86400000)SportsLeague(league,parseStandings(cached.getJSONObject("data"),league),cached.optLong("updated"),true)else throw e}
 }
}
