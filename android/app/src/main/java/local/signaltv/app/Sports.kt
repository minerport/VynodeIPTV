package local.signaltv.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import android.content.Context

data class SportsStat(val key:String,val label:String,val value:String,val numeric:Double?=null,val category:String="")
data class SportsRow(val id:String,val name:String,val group:String,val season:String,val phase:String,val stats:List<SportsStat>,val seasonYear:Int=0)
data class SportsLeague(val league:String,val rows:List<SportsRow>,val updated:Long,val stale:Boolean=false)
val sportsPaths=mapOf("NFL" to "football/nfl","NBA" to "basketball/nba","MLB" to "baseball/mlb","NHL" to "hockey/nhl","MLS" to "soccer/usa.1")
private val sportsFields=setOf("overall","wins","losses","ties","otLosses","points","winPercent","playoffSeed","rank","streak","pointsFor","pointsAgainst","pointDifferential","avgPointsFor","avgPointsAgainst","Home","Road","Last Ten Games")
fun parseStandings(root:JSONObject,league:String):List<SportsRow>{
 val rows=mutableListOf<SportsRow>()
 fun walk(n:JSONObject){val s=n.optJSONObject("standings");val entries=s?.optJSONArray("entries")
 if(entries!=null)for(i in 0 until entries.length()){val e=entries.getJSONObject(i);val t=e.getJSONObject("team");val a=e.optJSONArray("stats");val stats=if(a==null)emptyList()else (0 until a.length()).map{a.getJSONObject(it)}.filter{it.optString("name") in sportsFields&&it.has("displayValue")&&(it.optString("name")!="points"||league in setOf("NHL","MLS"))}.map{SportsStat(it.getString("name"),when(it.getString("name")){"overall"->"Record";"pointsFor"->if(league=="MLB")"Runs scored" else if(league in setOf("NHL","MLS"))"Goals for" else "Points for";"pointsAgainst"->if(league=="MLB")"Runs allowed" else if(league in setOf("NHL","MLS"))"Goals against" else "Points against";else->it.optString("displayName",it.getString("name"))},it.getString("displayValue"),it.optDouble("value",Double.NaN).takeIf{n->n.isFinite()})}
 rows.add(SportsRow(league.lowercase(java.util.Locale.ROOT)+":"+t.getString("id"),t.optString("displayName"),n.optString("name"),s.optString("seasonDisplayName",s.optString("season")),mapOf(1 to "Preseason",2 to "Regular season",3 to "Postseason")[s.optInt("seasonType")]?:"Season",stats,s.optInt("season")))}
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
 suspend fun data(kind:String,league:String,team:String="",season:String="",sort:String="",page:Int=1,phase:String=""):SportsPayload=withContext(Dispatchers.IO){
 val address=sportsPlayerAddress(kind,league,team,season,sort,page,phase);val key="data:"+profile.mode+":"+(if(profile.mode=="server")profile.base else "")+":"+address;val now=System.currentTimeMillis();val ttl=if(kind=="scoreboard")15000L else if(kind=="schedule")300000L else 900000L;val maxAge=if(kind in setOf("scoreboard","schedule"))300000L else 86400000L
 val cached=runCatching{JSONObject(prefs.getString(key,"")!!)}.getOrNull()
 fun saved(j:JSONObject,stale:Boolean)=SportsPayload(j.getJSONObject("data"),j.optLong("updated"),stale||j.optBoolean("stale"))
 if(cached!=null&&now-cached.optLong("updated") in 0..ttl)return@withContext saved(cached,false)
 try{val q=java.net.URLEncoder.encode(team,"UTF-8");val payload=if(profile.mode=="server")JSONObject(request(profile.base+"/api/v1/sports/data?kind="+kind+"&league="+league+"&team="+q+"&season="+season+"&sort="+java.net.URLEncoder.encode(sort,"UTF-8")+"&page="+page+"&seasontype="+phase,profile.token))else JSONObject().put("data",JSONObject(request(address))).put("updatedAt",java.time.Instant.ofEpochMilli(now).toString())
 require(payload.toString().length<=(if(kind in setOf("leaders","players"))8 else 2)*1024*1024);if(kind=="leaders")payload.put("data",sportsTrimLeaders(payload.getJSONObject("data")));val updated=programmeEpoch(payload.getString("updatedAt"));val entry=JSONObject().put("data",payload.getJSONObject("data")).put("updated",updated).put("stale",payload.optBoolean("stale"));val edit=prefs.edit();if(prefs.all.size>=64&&!prefs.contains(key))prefs.all.keys.firstOrNull()?.let{edit.remove(it)};edit.putString(key,entry.toString()).apply();saved(entry,false)
 }catch(e:CancellationException){throw e}catch(e:Exception){if(cached!=null&&now-cached.optLong("updated") in 0..maxAge)saved(cached,true)else throw e}
 }
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
