package local.signaltv.app

import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class SportsPayload(val data:JSONObject,val updated:Long,val stale:Boolean)
data class SportsCompetitor(val id:String,val name:String,val score:String="")
data class SportsGame(val id:String,val league:String,val name:String,val start:Long,val timeValid:Boolean,val state:String,val status:String,val detail:String,val teams:List<SportsCompetitor>,val updated:Long,val stale:Boolean,val scheduleOnly:Boolean=false,val lastPlay:String="",val possession:String="",val downDistance:String="")
data class SportsLeader(val id:String,val name:String,val team:String,val category:String,val value:String,val numeric:Double?,val rank:Int)
fun JSONArray.objects()=(0 until length()).mapNotNull{optJSONObject(it)}
fun sportsAddress(kind:String,league:String,team:String="",season:String=""):String{
 val path=requireNotNull(sportsPaths[league]);require(season.isBlank()||season.toIntOrNull() in 2020..(LocalDate.now().year+1));val base="https://site.api.espn.com/apis/"
 if(kind=="athlete"){require(team.length in 1..10&&team.all{it.isDigit()});return "https://site.web.api.espn.com/apis/common/v3/sports/$path/athletes/$team/stats"}
 val resource=when(kind){"standings"->"v2/sports/$path/standings";"leaders"->"site/v3/sports/$path/leaders";"scoreboard"->"site/v2/sports/$path/scoreboard";"schedule","statistics"->{require(team.startsWith(league.lowercase(java.util.Locale.ROOT)+":")&&team.substringAfter(':').all{it.isDigit()}&&team.substringAfter(':').isNotBlank());"site/v2/sports/$path/teams/"+team.substringAfter(':')+"/"+kind};else->error("Unknown sports resource")}
 if(kind=="scoreboard")return base+resource+"?limit=1000"
 return base+resource+if(season.isNotBlank())"?season=$season" else ""
}
fun sportsSourceSeason(data:JSONObject):String{val s=data.optJSONObject("requestedSeason")?:data.optJSONObject("season")?:JSONObject();return s.optString("displayName",s.optString("year","Season not supplied"))+" · "+(s.optJSONObject("type")?.optString("name")?:s.optString("name"))}
fun sportsDetailed(data:JSONObject):List<SportsStat> = data.optJSONObject("results")?.optJSONObject("stats")?.optJSONArray("categories")?.objects().orEmpty().flatMap{c->c.optJSONArray("stats")?.objects().orEmpty().filter{it.has("displayValue")}.map{s->SportsStat(c.optString("name")+"."+s.optString("name"),s.optString("displayName",s.optString("name")),s.optString("displayValue"),s.optDouble("value",Double.NaN).takeIf{it.isFinite()},c.optString("displayName",c.optString("name")))}}
fun sportsLeaders(data:JSONObject):List<SportsLeader> = data.optJSONObject("leaders")?.optJSONArray("categories")?.objects().orEmpty().flatMap{c->c.optJSONArray("leaders")?.objects().orEmpty().mapIndexedNotNull{i,x->val a=x.optJSONObject("athlete")?:return@mapIndexedNotNull null;SportsLeader(a.optString("id"),a.optString("displayName"),x.optJSONObject("team")?.optString("displayName").orEmpty(),c.optString("displayName",c.optString("name")),x.optString("displayValue"),x.optDouble("value",Double.NaN).takeIf{it.isFinite()},i+1)}}
fun sportsGames(payload:SportsPayload,league:String,scheduleOnly:Boolean=false):List<SportsGame> = payload.data.optJSONArray("events")?.objects().orEmpty().mapNotNull{e->
 val c=e.optJSONArray("competitions")?.optJSONObject(0)?:return@mapNotNull null;val status=c.optJSONObject("status")?:e.optJSONObject("status");val type=status?.optJSONObject("type");val teams=c.optJSONArray("competitors")?.objects().orEmpty().mapNotNull{x->val t=x.optJSONObject("team")?:return@mapNotNull null;SportsCompetitor(league.lowercase(java.util.Locale.ROOT)+":"+t.optString("id"),t.optString("displayName"),x.optJSONObject("score")?.optString("displayValue")?:x.optString("score"))};val start=runCatching{programmeEpoch(c.optString("date",e.optString("date")))}.getOrNull()?:return@mapNotNull null
 if(teams.size!=2||e.optString("id").isBlank())return@mapNotNull null
 SportsGame(league+":"+e.getString("id"),league,e.optString("name"),start,c.optBoolean("timeValid",true)&&e.optBoolean("timeValid",true),type?.optString("state","unknown")?:"unknown",type?.optString("name","UNKNOWN")?:"UNKNOWN",type?.optString("detail",type.optString("description","Status unavailable"))?:"Status unavailable",teams,payload.updated,payload.stale,scheduleOnly,c.optJSONObject("situation")?.optJSONObject("lastPlay")?.optString("text")?.trim()?.take(600).orEmpty(),c.optJSONObject("situation")?.optString("possession")?.takeIf{it.isNotBlank()}?.let{league.lowercase(java.util.Locale.ROOT)+":"+it}.orEmpty(),c.optJSONObject("situation")?.optString("downDistanceText")?.take(100).orEmpty())
}.distinctBy{it.id}
fun sportsLive(g:SportsGame,now:Long)=!g.scheduleOnly&&!g.stale&&now-g.updated in 0..90000&&g.state=="in"&&!Regex("DELAY|SUSPEND|POSTPON|CANCEL|FINAL",RegexOption.IGNORE_CASE).containsMatchIn(g.status)
fun sportsStatus(g:SportsGame,now:Long)=if(g.stale||(g.state=="in"&&now-g.updated !in 0..90000))"Status needs refresh" else if(sportsLive(g,now))"LIVE · "+g.detail else g.detail
fun sportsSort(rows:List<SportsRow>,key:String,descending:Boolean)=rows.sortedWith(Comparator{a,b->if(key=="name")return@Comparator (if(descending)-1 else 1)*a.name.compareTo(b.name);val x=a.stats.find{it.key==key}?.numeric;val y=b.stats.find{it.key==key}?.numeric;when{ x==null&&y==null->a.name.compareTo(b.name);x==null->1;y==null->-1;else->(if(descending)y.compareTo(x)else x.compareTo(y)).takeIf{it!=0}?:a.name.compareTo(b.name)}})
fun sportsChannels(game:SportsGame,channels:List<Channel>,hits:List<InterestHit>,catalog:InterestCatalog):List<Channel>{
 val teams=game.teams.mapNotNull{v->catalog.teams.find{it.id==v.id}};if(teams.size!=2)return emptyList()
 return channels.filter{c->val station=interestWords(c.name+" "+c.groupName);val dedicated=teams.any{t->interestHas(station,t.name)||(interestHas(station,t.league)&&t.aliases.any{interestHas(station,it)})};dedicated||hits.any{h->if(h.channel.id!=c.id||Regex("\\b(replay|classic|highlights|encore)\\b",RegexOption.IGNORE_CASE).containsMatchIn(h.programme.title)||h.programme.start>game.start+21600000||h.programme.end<game.start-7200000)return@any false;val text=interestWords(h.programme.title);val named=catalog.teams.filter{it.league==game.league&&(listOf(it.name)+it.aliases).any{a->interestHas(text,a)}};named.size==2&&teams.all{t->named.any{it.id==t.id}}}}
}

data class SportsAthleteSeason(val category:String,val season:String,val year:Int,val team:String,val stats:List<SportsStat>)
fun sportsAthlete(data:JSONObject):List<SportsAthleteSeason> = data.optJSONArray("categories")?.objects().orEmpty().flatMap{c->val names=c.optJSONArray("names")?:JSONArray();val labels=c.optJSONArray("displayNames")?:names;c.optJSONArray("statistics")?.objects().orEmpty().map{s->val values=s.optJSONArray("stats")?:JSONArray();SportsAthleteSeason(c.optString("displayName",c.optString("name")),s.optJSONObject("season")?.optString("displayName").orEmpty(),s.optJSONObject("season")?.optInt("year")?:0,data.optJSONObject("teams")?.optJSONObject(s.optString("teamSlug"))?.optString("displayName")?:s.optString("teamSlug"),(0 until names.length()).map{i->val v=values.optString(i,"—");SportsStat(names.optString(i),labels.optString(i),v,v.replace(",","").removeSuffix("%").toDoubleOrNull())})}}

fun sportsMerge(schedules:List<SportsGame>,scoreboard:List<SportsGame>):List<SportsGame>{val map=linkedMapOf<String,SportsGame>();for(g in schedules)if(map[g.id]==null||map.getValue(g.id).updated<g.updated)map[g.id]=g;for(g in scoreboard)if(!g.stale||map[g.id]==null)map[g.id]=g;return map.values.toList()}

fun sportsTrimLeaders(data:JSONObject):JSONObject{
 val result=JSONObject();for(k in listOf("currentSeason","requestedSeason"))if(data.has(k))result.put(k,data.get(k))
 val categories=org.json.JSONArray();val original=data.optJSONObject("leaders")?.optJSONArray("categories")
 if(original!=null)for(i in 0 until original.length()){val c=original.getJSONObject(i);val output=JSONObject().put("name",c.optString("name")).put("displayName",c.optString("displayName"));val leaders=org.json.JSONArray();val source=c.optJSONArray("leaders")
 if(source!=null)for(j in 0 until source.length()){val v=source.getJSONObject(j);val row=JSONObject();for(k in listOf("value","displayValue"))if(v.has(k))row.put(k,v.get(k));for(k in listOf("athlete","team")){val item=v.optJSONObject(k);if(item!=null)row.put(k,JSONObject().put("id",item.optString("id")).put("displayName",item.optString("displayName")))};leaders.put(row)};categories.put(output.put("leaders",leaders))}
 return result.put("leaders",JSONObject().put("categories",categories))
}

fun sportsTeamGames(games:List<SportsGame>,teams:List<String>,now:Long)=games.filter{g->g.teams.any{it.id in teams}&&(g.start>now-86400000||(g.state=="pre"&&!Regex("CANCEL|FINAL").containsMatchIn(g.status)))}
