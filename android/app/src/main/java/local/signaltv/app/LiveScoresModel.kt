package local.signaltv.app

import org.json.JSONObject
import org.json.JSONArray

val scorePositions=listOf("top-left","top-center","top-right","bottom-left","bottom-center","bottom-right")
data class ScorePreferences(val visible:Boolean=false,val mode:String="sport",val league:String="NFL",val automatic:Boolean=true,val position:String="bottom-left",val size:String="compact",val opacity:Int=80,val accent:String="red",val rows:Int=3,val rotate:Boolean=false,val pins:List<String> = emptyList()){
 fun json()=JSONObject().put("visible",visible).put("mode",mode).put("league",league).put("automatic",automatic).put("position",position).put("size",size).put("opacity",opacity).put("accent",accent).put("rows",rows).put("rotate",rotate).put("pins",JSONArray(pins))
 companion object{fun parse(j:JSONObject):ScorePreferences{fun choice(key:String,options:List<String>,fallback:String)=j.optString(key).takeIf{it in options}?:fallback
 return ScorePreferences(j.optBoolean("visible"),choice("mode",listOf("sport","all","pinned"),"sport"),choice("league",sportsPaths.keys.toList(),"NFL"),j.optBoolean("automatic",true),choice("position",scorePositions,"bottom-left"),choice("size",listOf("compact","large"),"compact"),j.optInt("opacity").takeIf{it in listOf(60,80,95)}?:80,choice("accent",listOf("red","gold","ice"),"red"),j.optInt("rows").takeIf{it in listOf(1,3,5)}?:3,j.optBoolean("rotate"),j.optJSONArray("pins")?.let{a->(0 until a.length()).map{a.optString(it)}.filter{Regex("(NFL|NBA|MLB|NHL|MLS):[0-9]+").matches(it)}.distinct().take(12)}.orEmpty())}}
}
fun scoreContext(channel:Channel,title:String,catalog:InterestCatalog):String?{val text=interestWords(channel.name+" "+channel.groupName+" "+title);val found=(sportsPaths.keys.filter{interestHas(text,it)}+catalog.teams.filter{interestHas(text,it.name)}.map{it.league}).distinct();return found.singleOrNull()}
fun scoreWatched(game:SportsGame,title:String,catalog:InterestCatalog):Boolean{if(Regex("\\b(replay|classic|highlights|encore)\\b",RegexOption.IGNORE_CASE).containsMatchIn(title))return false;val text=interestWords(title);return game.teams.size==2&&game.teams.all{t->catalog.teams.find{it.id==t.id}?.let{team->(listOf(team.name)+team.aliases).any{interestHas(text,it)}}==true}}
fun scoreGames(games:List<SportsGame>,prefs:ScorePreferences,league:String,title:String,catalog:InterestCatalog,now:Long)=games.filter{g->sportsLive(g,now)&&(if(prefs.mode=="pinned")g.id in prefs.pins else prefs.mode=="all"||g.league==league)&&(prefs.mode=="pinned"||!scoreWatched(g,title,catalog))}.sortedWith(if(prefs.mode=="pinned")compareBy{prefs.pins.indexOf(it.id)}else compareBy<SportsGame>{it.league}.thenBy{it.start})
fun scorePageCount(count:Int,rows:Int)=((count+rows-1)/rows).coerceAtLeast(1)
