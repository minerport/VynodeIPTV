package local.signaltv.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

enum class GuideDensity(val label:String,val row:Int,val text:Int){
    COMPACT("Compact",44,14), COMFORTABLE("Comfortable",54,16), LARGE("Large text",64,19)
}
class GuideExperience(context:Context,profile:Profile){
    private val prefs=context.getSharedPreferences("guide-experience",Context.MODE_PRIVATE)
    private val key=profile.accountKey()
    fun density()=runCatching{GuideDensity.valueOf(prefs.getString("$key-density","COMPACT")!!)}.getOrDefault(GuideDensity.COMPACT)
    fun density(value:GuideDensity){prefs.edit().putString("$key-density",value.name).apply()}
    fun artwork()=prefs.getBoolean("$key-artwork",true)
    fun artwork(value:Boolean){prefs.edit().putBoolean("$key-artwork",value).apply()}
    fun recent():List<String> = runCatching{val a=JSONArray(prefs.getString("$key-recent","[]"));(0 until a.length()).map{a.getString(it)}}.getOrDefault(emptyList())
    fun watched(id:String){prefs.edit().putString("$key-recent",JSONArray((listOf(id)+recent().filter{it!=id}).take(20)).toString()).apply()}
}
data class GuideSearchHit(val key:String,val kind:String,val title:String,val channel:Channel?=null,val start:Long?=null)
fun searchGuide(query:String,channels:List<Channel>,guide:Map<String,List<Programme>>,now:Long):List<GuideSearchHit>{
    val terms=query.trim().lowercase().split(Regex("\\s+")).filter{it.isNotEmpty()}
    if(terms.isEmpty())return emptyList()
    fun matches(value:String)=terms.all{value.lowercase().contains(it)}
    val hits=channels.filter{matches(it.name+" "+it.groupName)}.sortedBy{it.name.lowercase()}.map{GuideSearchHit("c:${it.id}","Channel",it.name,it)}
    val programmes=channels.flatMap{c->guide[c.id].orEmpty().filter{it.end>now&&it.start<now+7*86400000L&&matches(it.title+" "+it.episodeTitle)}.map{GuideSearchHit("p:${c.id}:${it.start}","Programme",it.title,c,it.start)}}.sortedBy{it.start}
    return (hits+programmes).distinctBy{it.key}.take(100)
}

@Composable fun SearchEverywhere(repo:Repository,channels:List<Channel>,cached:Map<String,List<Programme>>,dismiss:()->Unit,select:(GuideSearchHit)->Unit){
    val context=LocalContext.current
    var query by remember{mutableStateOf("")}
    var hits by remember{mutableStateOf(emptyList<GuideSearchHit>())}
    var status by remember{mutableStateOf("Search channels, programmes and recordings")}
    LaunchedEffect(query){
        hits=emptyList()
        if(query.isBlank()){status="Search channels, programmes and recordings";return@LaunchedEffect}
        status="Searching…";delay(300)
        try{
            hits=withContext(Dispatchers.IO){
                if(repo.profile.mode=="server"){
                    val result=JSONObject(request(repo.profile.base+"/api/v1/search?q="+android.net.Uri.encode(query),repo.profile.token)).getJSONArray("results")
                    (0 until result.length()).map{n->val r=result.getJSONObject(n);val c=r.optJSONObject("channel")?.let{j->channels.find{it.id==j.optString("id")}};val p=r.optJSONObject("programme");val job=r.optJSONObject("recording")
                        GuideSearchHit("server:$n",r.getString("kind"),p?.optString("title")?:job?.optString("title")?:c?.name.orEmpty(),c,p?.optString("start")?.let{Instant.parse(it).toEpochMilli()})}
                }else{
                    val stored=EpgCache(context,repo.profile).load()
                    val all=(stored.keys+cached.keys).associateWith{id->(cached[id].orEmpty()+stored[id].orEmpty()).distinctBy{it.start to it.title}}
                    val local=searchGuide(query,channels,all,System.currentTimeMillis())
                    val terms=query.trim().lowercase().split(Regex("\\s+"))
                    (local+RecordingStore.list(context).filter{it.account==repo.profile.accountKey()&&terms.all{term->it.title.lowercase().contains(term)}}.map{GuideSearchHit("r:${it.id}","recording",it.title)}).take(100)
                }
            }
            status=if(hits.isEmpty())"No matches in available guide data" else "${hits.size} results${if(hits.size==100)" · narrow your search for more" else ""}"
        }catch(e:CancellationException){throw e}catch(_:Exception){status="Search unavailable. Server search requires the updated server; channel filtering remains available."}
    }
    AlertDialog(onDismissRequest=dismiss,title={Text("Search everything")},text={Column{
        TvTextInput(query,{query=it.take(100)},label={Text("Channel or programme title")},singleLine=true)
        Text(status,style=MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.height(280.dp)){items(hits,key={it.key}){hit->TextButton(onClick={select(hit)},modifier=Modifier.fillMaxWidth()){
            Column(Modifier.fillMaxWidth()){Text(hit.title,maxLines=1);Text(listOfNotNull(hit.kind,hit.channel?.name,hit.start?.let{Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("EEE HH:mm"))}).joinToString(" · "),style=MaterialTheme.typography.bodySmall)}
        }}}
    }},confirmButton={TextButton(onClick=dismiss){Text("Close")}})
}
