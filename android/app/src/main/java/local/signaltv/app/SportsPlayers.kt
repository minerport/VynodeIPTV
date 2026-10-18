package local.signaltv.app

import org.json.JSONObject
import java.net.URLEncoder

val sportsPlayerCategories=mapOf(
 "NFL" to listOf("Passing" to "passing.passingYards","Rushing" to "rushing.rushingYards","Receiving" to "receiving.receivingYards","Defense" to "defensive.totalTackles","Kicking" to "kicking.fieldGoalsMade"),
 "NBA" to listOf("Scoring" to "offensive.avgPoints","Rebounds" to "general.avgRebounds","Assists" to "offensive.avgAssists","Defense" to "defensive.avgSteals"),
 "MLB" to listOf("Batting" to "batting.avg","Home runs" to "batting.homeRuns","Pitching" to "pitching.ERA"),
 "NHL" to listOf("Skaters" to "offensive.points","Goals" to "offensive.goals","Goaltending" to "defensive.savePct"),
 "MLS" to listOf("Scoring" to "offensive.goals")
)
data class SportsPlayerColumn(val key:String,val label:String,val title:String,val category:String)
data class SportsPlayer(val id:String,val name:String,val team:String,val position:String,val stats:List<SportsStat>)
data class SportsPlayerTable(val columns:List<SportsPlayerColumn> = emptyList(),val players:List<SportsPlayer> = emptyList(),val page:Int=1,val pages:Int=1,val count:Int=0)
fun sportsPlayerAddress(kind:String,league:String,team:String,season:String,sort:String,page:Int,phase:String):String{
 if(kind!="players")return sportsAddress(kind,league,team,season)
 val path=requireNotNull(sportsPaths[league]);require(page in 1..100);require(phase in listOf("","1","2","3"));require(sort.isBlank()||Regex("[a-zA-Z][a-zA-Z0-9]{0,39}\\.[a-zA-Z][a-zA-Z0-9]{0,49}:(asc|desc)").matches(sort))
 sportsAddress("standings",league,season=season)
 val base=if(team.isBlank())"https://site.web.api.espn.com/apis/common/v3/sports/$path/statistics/byathlete" else {sportsAddress("statistics",league,team,season);"https://site.api.espn.com/apis/site/v2/sports/$path/teams/${team.substringAfter(':')}/athletes/statistics"}
 return base+"?limit=50&page=$page"+(if(season.isNotBlank())"&season=$season" else "")+(if(phase.isNotBlank())"&seasontype=$phase" else "")+(if(sort.isNotBlank())"&sort="+URLEncoder.encode(sort,"UTF-8") else "")
}
fun sportsPlayers(data:JSONObject):SportsPlayerTable{
 val columns=linkedMapOf<String,SportsPlayerColumn>();val people=linkedMapOf<String,SportsPlayer>()
 if(data.has("athletes")){
 val definitions=data.optJSONArray("categories")?.objects().orEmpty()
 for(c in definitions){val names=c.optJSONArray("names")?:continue;for(i in 0 until names.length()){val key=c.optString("name")+"."+names.optString(i);columns[key]=SportsPlayerColumn(key,c.optJSONArray("labels")?.optString(i)?:names.optString(i),c.optJSONArray("displayNames")?.optString(i)?:names.optString(i),c.optString("name"))}}
 for(p in data.optJSONArray("athletes")?.objects().orEmpty()){val a=p.optJSONObject("athlete")?:continue;val stats=mutableListOf<SportsStat>();for(c in p.optJSONArray("categories")?.objects().orEmpty()){val def=definitions.find{it.optString("name")==c.optString("name")}?:continue;val names=def.optJSONArray("names")?:continue;for(i in 0 until names.length()){val key=c.optString("name")+"."+names.optString(i);stats+=SportsStat(key,columns[key]?.title.orEmpty(),c.optJSONArray("totals")?.optString(i,"—")?:"—",c.optJSONArray("values")?.optDouble(i,Double.NaN)?.takeIf{it.isFinite()})}};val id=a.optString("id");people[id]=SportsPlayer(id,a.optString("displayName"),a.optJSONArray("teams")?.optJSONObject(0)?.optString("abbreviation")?:a.optString("teamShortName"),a.optJSONObject("position")?.optString("abbreviation").orEmpty(),stats)}
 }else{
 for(c in data.optJSONArray("results")?.objects().orEmpty())for(p in c.optJSONArray("leaders")?.objects().orEmpty()){val a=p.optJSONObject("athlete")?:continue;val id=a.optString("id");val old=people[id];val stats=old?.stats.orEmpty().toMutableList();for(block in p.optJSONArray("statistics")?.objects().orEmpty())for(s in block.optJSONArray("stats")?.objects().orEmpty()){val key=block.optString("name")+"."+s.optString("name");columns[key]=SportsPlayerColumn(key,s.optString("abbreviation",s.optString("name")),s.optString("displayName",s.optString("name")),block.optString("name"));if(stats.none{it.key==key})stats+=SportsStat(key,s.optString("displayName"),s.optString("displayValue","—"),s.optDouble("value",Double.NaN).takeIf{it.isFinite()})};people[id]=SportsPlayer(id,a.optString("displayName"),data.optJSONObject("team")?.optString("abbreviation").orEmpty(),a.optJSONObject("position")?.optString("abbreviation").orEmpty(),stats)}
 }
 val page=data.optJSONObject("pagination");return SportsPlayerTable(columns.values.toList(),people.values.toList(),page?.optInt("page",1)?:1,page?.optInt("pages",1)?:1,page?.optInt("count",people.size)?:people.size)
}
val sportsPreferredColumns=mapOf(
 "offensive" to listOf("avgPoints","avgAssists","fieldGoalPct","threePointFieldGoalPct","freeThrowPct","avgTurnovers","points","goals","assists","powerPlayGoals","shotsTotal"),
 "general" to listOf("avgRebounds","gamesPlayed","avgMinutes","doubleDouble","tripleDouble","games","wins","losses","plusMinus","timeOnIcePerGame"),
 "passing" to listOf("passingYards","completions","passingAttempts","completionPct","passingTouchdowns","interceptions","QBRating"),
 "rushing" to listOf("rushingYards","rushingAttempts","yardsPerRushAttempt","rushingTouchdowns","longRushing","rushingYardsPerGame"),
 "receiving" to listOf("receivingYards","receptions","receivingTargets","yardsPerReception","receivingTouchdowns","longReception"),
 "defensive" to listOf("totalTackles","soloTackles","assistTackles","sacks","passesDefended","avgSteals","avgBlocks","savePct","avgGoalsAgainst","saves"),
 "batting" to listOf("avg","homeRuns","RBIs","hits","runs","onBasePct","OPS"),
 "pitching" to listOf("ERA","wins","losses","innings","strikeouts","WHIP","saves")
)
fun sportsPlayerColumns(table:SportsPlayerTable,metric:String):List<SportsPlayerColumn>{val c=metric.substringBefore('.');return(listOf(metric)+sportsPreferredColumns[c].orEmpty().map{"$c.$it"}+table.columns.filter{it.category==c}.map{it.key}).distinct().mapNotNull{k->table.columns.find{it.key==k}}.take(7)}
