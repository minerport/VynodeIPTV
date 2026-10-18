package local.signaltv.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class InterestSettings(val teams:List<String> = emptyList(),val city:String="",val state:String="",val genres:List<String> = emptyList(),val localChannels:List<String> = emptyList()){
 fun json()=JSONObject().put("teams",JSONArray(teams)).put("city",city.trim()).put("state",state.trim()).put("genres",JSONArray(genres)).put("localChannels",JSONArray(localChannels))
 companion object{fun parse(j:JSONObject)=InterestSettings(j.strings("teams"),j.optString("city"),j.optString("state"),j.strings("genres"),j.strings("localChannels"))}
}
fun JSONObject.strings(key:String):List<String>{val a=optJSONArray(key)?:return emptyList();return (0 until a.length()).map{a.getString(it)}}
data class InterestTeam(val id:String,val name:String,val league:String,val abbreviation:String,val color:String,val logo:String,val aliases:List<String>)
data class InterestCatalog(val teams:List<InterestTeam>,val genres:Map<String,List<String>>,val states:Map<String,String> = emptyMap()){companion object{fun load(c:Context):InterestCatalog{val j=JSONObject(c.assets.open("interests-catalog.json").bufferedReader().use{it.readText()});val a=j.getJSONArray("teams");val g=j.getJSONObject("genres");return InterestCatalog((0 until a.length()).map{a.getJSONObject(it).let{t->InterestTeam(t.getString("id"),t.getString("name"),t.getString("league"),t.getString("abbreviation"),t.getString("color"),t.getString("logo"),t.strings("aliases"))}},g.keys().asSequence().associateWith{g.strings(it)},j.getJSONObject("states").let{s->s.keys().asSequence().associateWith{s.getString(it)}})}}}
data class InterestHit(val channel:Channel,val programme:Programme,val section:String,val reason:String,val teamId:String="")
private val interestPunctuation=Regex("[^\\p{L}\\p{N}]+")
fun interestWords(s:String)=" "+s.lowercase(java.util.Locale.ROOT).replace(interestPunctuation," ").trim()+" "
fun interestHas(text:String,phrase:String)=phrase.isNotBlank()&&text.contains(interestWords(phrase))
fun matchInterests(p:InterestSettings,catalog:InterestCatalog,channels:List<Channel>,guide:Map<String,List<Programme>>,hidden:Set<String>,now:Long):List<InterestHit>{
 val tokens=mutableMapOf<String,String>()
 fun has(text:String,phrase:String)=phrase.isNotBlank()&&text.contains(tokens.getOrPut(phrase){interestWords(phrase)})
 val teams=catalog.teams.filter{it.id in p.teams};val hits=mutableListOf<InterestHit>()
 for(c in channels){if("provider:"+c.groupId in hidden)continue;val station=interestWords(c.name+" "+c.groupName)
 for(v in guide[c.id].orEmpty()){if(v.end<=now||v.end<=v.start||v.start>=now+7*86400000L)continue;val text=interestWords(v.title+" "+v.episodeTitle+" "+v.description+" "+v.genres)
 val team=teams.firstOrNull{t->val sport=mapOf("NFL" to "football","NBA" to "basketball","MLB" to "baseball","NHL" to "hockey","MLS" to "soccer")[t.league].orEmpty();has(text,t.name)||((has(text,t.league)||has(text,sport))&&t.aliases.any{has(text,it)})}
 if(team!=null)hits.add(InterestHit(c,v,"sports",team.name,team.id))
 if(c.id in p.localChannels)hits.add(InterestHit(c,v,"local","Your selected local station"))else if(p.city.isNotBlank()&&p.state.isNotBlank()&&has(station,p.city)&&interestState(station,p.state,catalog.states))hits.add(InterestHit(c,v,"local",p.city+", "+p.state+" · channel listing match"))
 val genre=p.genres.firstOrNull{g->catalog.genres[g].orEmpty().any{has(if(v.genres.isBlank())text else interestWords(v.genres),it)}}
 if(genre!=null)hits.add(InterestHit(c,v,"genres",genre+if(v.genres.isBlank())" · guide text match" else " · EPG category"))
 }}
 return hits.distinctBy{Triple(it.section,it.channel.id,it.programme.start)}.sortedBy{it.programme.start}.groupBy{it.section+(if(it.programme.start>now)"-next" else "")}.values.flatMap{it.take(40)}
}
class InterestStore(val context:Context,val profile:Profile){
 private val prefs=context.getSharedPreferences("personal-interests",Context.MODE_PRIVATE)
 suspend fun load():InterestSettings=withContext(Dispatchers.IO){if(profile.mode=="server")InterestSettings.parse(JSONObject(request(profile.base+"/api/v1/interests",profile.token)))else InterestSettings.parse(JSONObject(prefs.getString(profile.accountKey(),"{}")!!))}
 suspend fun save(p:InterestSettings)=withContext(Dispatchers.IO){if(profile.mode=="server")request(profile.base+"/api/v1/interests",profile.token,"PUT",p.json())else{check(prefs.edit().putString(profile.accountKey(),p.json().toString()).commit()){"Could not save interests"};""}}
 suspend fun hits(channels:List<Channel>,cached:Map<String,List<Programme>>,catalog:InterestCatalog):List<InterestHit> = withContext(Dispatchers.IO){
 if(profile.mode=="server"){val a=JSONObject(request(profile.base+"/api/v1/for-you",profile.token)).getJSONArray("results");val byId=channels.associateBy{it.id};(0 until a.length()).mapNotNull{val h=a.getJSONObject(it);val c=byId[h.getJSONObject("channel").getString("id")]?:return@mapNotNull null;val v=h.getJSONObject("programme");InterestHit(c,Programme(v.getString("title"),programmeEpoch(v.getString("start")),programmeEpoch(v.getString("end")),v.optString("description"),v.optString("year"),v.optInt("season"),v.optInt("episode"),v.optString("episodeTitle"),v.optString("genres")),h.getString("section"),h.getString("reason"),h.optString("teamId"))}}
 else{val stored=EpgCache(context,profile).load();val all=(stored.keys+cached.keys).associateWith{id->(cached[id].orEmpty()+stored[id].orEmpty()).distinctBy{it.start to it.title}};matchInterests(load(),catalog,channels,all,Groups(context,profile).preferences().hiddenGroups,System.currentTimeMillis())}
 }
}

fun interestState(text:String,state:String,states:Map<String,String>):Boolean=interestHas(text,state)||states.any{(code,name)->(code.equals(state,true)||name.equals(state,true))&&(interestHas(text,code)||interestHas(text,name))}
