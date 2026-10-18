package local.signaltv.app

import org.json.JSONObject
import org.json.JSONArray

val scorePositions=listOf("top-left","top-center","top-right","bottom-left","bottom-center","bottom-right")
data class ScorePreferences(val visible:Boolean=false,val mode:String="sport",val league:String="NFL",val automatic:Boolean=true,val position:String="bottom-left",val size:String="compact",val opacity:Int=80,val accent:String="red",val rows:Int=3,val rotate:Boolean=false,val pins:List<String> = emptyList(),val lastPlay:Boolean=true,val delay:Int=0){
 fun json()=JSONObject().put("visible",visible).put("mode",mode).put("league",league).put("automatic",automatic).put("position",position).put("size",size).put("opacity",opacity).put("accent",accent).put("rows",rows).put("rotate",rotate).put("pins",JSONArray(pins)).put("lastPlay",lastPlay).put("delay",delay)
 companion object{fun parse(j:JSONObject):ScorePreferences{fun choice(key:String,options:List<String>,fallback:String)=j.optString(key).takeIf{it in options}?:fallback
 return ScorePreferences(j.optBoolean("visible"),choice("mode",listOf("sport","all","pinned"),"sport"),choice("league",sportsPaths.keys.toList(),"NFL"),j.optBoolean("automatic",true),choice("position",scorePositions,"bottom-left"),choice("size",listOf("compact","large"),"compact"),j.optInt("opacity").takeIf{it in listOf(60,80,95)}?:80,choice("accent",listOf("red","gold","ice"),"red"),j.optInt("rows").takeIf{it in listOf(1,3,5)}?:3,j.optBoolean("rotate"),j.optJSONArray("pins")?.let{a->(0 until a.length()).map{a.optString(it)}.filter{Regex("(NFL|NBA|MLB|NHL|MLS):[0-9]+").matches(it)}.distinct().take(12)}.orEmpty(),j.optBoolean("lastPlay",true),j.optInt("delay").takeIf{it in listOf(0,15,30,60,90,120)}?:0)}}
}
fun scoreContext(channel:Channel,title:String,catalog:InterestCatalog):String?{val text=interestWords(channel.name+" "+channel.groupName+" "+title);val found=(sportsPaths.keys.filter{interestHas(text,it)}+catalog.teams.filter{interestHas(text,it.name)}.map{it.league}).distinct();return found.singleOrNull()}
fun scoreWatched(game:SportsGame,title:String,catalog:InterestCatalog):Boolean{if(Regex("\\b(replay|classic|highlights|encore)\\b",RegexOption.IGNORE_CASE).containsMatchIn(title))return false;val text=interestWords(title);return game.teams.size==2&&game.teams.all{t->catalog.teams.find{it.id==t.id}?.let{team->(listOf(team.name)+team.aliases).any{interestHas(text,it)}}==true}}
fun scoreGames(games:List<SportsGame>,prefs:ScorePreferences,league:String,title:String,catalog:InterestCatalog,now:Long)=games.filter{g->scoreAvailable(g,now)&&(if(prefs.mode=="pinned")g.id in prefs.pins else prefs.mode=="all"||g.league==league)&&(prefs.mode=="pinned"||!scoreWatched(g,title,catalog))}.sortedWith(if(prefs.mode=="pinned")compareBy{prefs.pins.indexOf(it.id)}else compareBy<SportsGame>{it.league}.thenBy{it.start})
fun scorePageCount(count:Int,rows:Int)=((count+rows-1)/rows).coerceAtLeast(1)

class ScoreHistory {
 private data class Snapshot(val league:String,val games:List<SportsGame>,val at:Long)
 private val entries=mutableListOf<Snapshot>()
 fun add(league:String,games:List<SportsGame>,at:Long){entries.removeAll{at-it.at>300000};entries.add(Snapshot(league,games,at));if(entries.size>160)entries.removeAt(0)}
 fun games(delay:Int,now:Long):List<SportsGame>{val chosen=linkedMapOf<String,List<SportsGame>>();for(e in entries)if(e.at<=now-delay*1000L&&now-e.at<=300000)chosen[e.league]=e.games;return chosen.values.flatten()}
 fun ready(delay:Int,now:Long)=entries.any{it.at<=now-delay*1000L&&now-it.at<=300000}
}
fun scoreAge(updated:Long,now:Long):String{val age=((now-updated)/1000).coerceAtLeast(0);return "Updated "+(if(age<60)"${age}s" else "${age/60}m ${age%60}s")+" ago"}

fun scoreAvailable(g:SportsGame,now:Long)=!g.scheduleOnly&&now-g.updated in 0..300000&&g.state=="in"&&!Regex("DELAY|SUSPEND|POSTPON|CANCEL|FINAL",RegexOption.IGNORE_CASE).containsMatchIn(g.status)
