package local.signaltv.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.json.JSONArray

fun discoverChannels(channels:List<Channel>,favorites:Set<String>,recent:List<String>):List<Channel>{val ids=channels.associateBy{it.id};return (favorites+recent+channels.map{it.id}).distinct().mapNotNull{ids[it]}.take(12)}
@Composable fun DiscoverScreen(channels:List<Channel>,repo:Repository,state:GuideUiState,guide:()->Unit,wall:()->Unit,settings:()->Unit,play:(Channel)->Unit){
 var personal by remember{mutableStateOf(true)}
 if(personal){ForYouScreen(channels,repo,state,guide,{personal=false},play);return}
 val context=LocalContext.current
 var favorites by remember(repo.profile){mutableStateOf(emptySet<String>())}
 val recent=remember(repo.profile){GuideExperience(context,repo.profile).recent()}
 var entries by remember(repo.profile){mutableStateOf(emptyMap<String,List<Programme>>())}
 var recordings by remember(repo.profile){mutableStateOf(emptyList<Pair<String,String>>())}
 var notice by remember{mutableStateOf("")}
 val metadata=remember(repo.profile){mutableStateMapOf<String,org.json.JSONObject>()}
 val selected=remember(channels,favorites,recent){discoverChannels(channels,favorites,recent)}
 fun library(){context.startActivity(android.content.Intent(context,RecordingActivity::class.java).putExtra("account",repo.profile.accountKey()))}
 LaunchedEffect(repo.profile){try{favorites=Groups(context,repo.profile).preferences().favorites}catch(_:Exception){};try{recordings=withContext(Dispatchers.IO){if(repo is ServerRepository){val a=JSONArray(request(repo.profile.base+"/api/v1/recordings",repo.profile.token));(0 until a.length()).map{a.getJSONObject(it).let{j->j.getString("title") to j.getString("status")}}}else RecordingStore.list(context).filter{it.account==repo.profile.accountKey()}.map{it.title to it.status}}.takeLast(12).reversed()}catch(e:CancellationException){throw e}catch(_:Exception){notice="Recordings unavailable right now."}}
 LaunchedEffect(repo.profile,selected){
  val now=System.currentTimeMillis();entries=selected.associate{it.id to state.cache[it.id]?.second.orEmpty()}
  try{entries=if(repo is ServerRepository)repo.programmeBatch(selected,now,now+86400000)else withContext(Dispatchers.IO){val cached=EpgCache(context,repo.profile).load();selected.associate{it.id to cached[it.id].orEmpty().ifEmpty{state.cache[it.id]?.second.orEmpty()}}};notice="Highlights from up to 12 channels. Open the guide for your full lineup."}catch(e:CancellationException){throw e}catch(_:Exception){notice="Programme highlights unavailable. Channels can still play."}
 }
 LaunchedEffect(repo.profile,entries){val service=TmdbMetadata.get(context);val now=System.currentTimeMillis();for(p in entries.values.flatten().filter{it.end>now}.take(24)){try{service.lookup(repo.profile,p)?.let{metadata[programmeMetadataKey(p)]=it}}catch(e:CancellationException){throw e}catch(_:Exception){};delay(150)}}
 BackHandler{guide()}
 LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp)){
  item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){TextButton(onClick={personal=true}){Text("For you")};Text("COLLECTION",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.weight(1f));TextButton(onClick=guide){Text("Guide")};TextButton(onClick=wall){Text("Wall")};TextButton(onClick={library()}){Text("Recordings")};TextButton(onClick=settings){Text("Appearance")}}}
  item{Text("Find something worth watching.",style=MaterialTheme.typography.headlineSmall)}
  val sections=listOf("Recently watched" to recent.mapNotNull{id->channels.find{it.id==id}}.take(12),"Favorites" to channels.filter{it.id in favorites}.take(12),"On now" to selected)
  sections.forEach{(title,list)->
   item{Text(title,style=MaterialTheme.typography.titleLarge)}
   item{if(list.isEmpty())Text("Nothing here yet. Browse the guide to build your collection.",color=TvPalette.Muted)else LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){items(list,key={it.id}){c->val p=currentProgramme(entries[c.id].orEmpty(),System.currentTimeMillis());DiscoverCard(c.name,p?.title?:"Watch live",metadata[p?.let{programmeMetadataKey(it)}]){play(c)}}}}
  }
  item{Text("Upcoming on your favorites",style=MaterialTheme.typography.titleLarge)}
  item{val next=entries.flatMap{(id,list)->list.map{Triple(id,it.start,it)}}.filter{it.first in favorites&&it.second>System.currentTimeMillis()}.sortedBy{it.second}.take(12)
   if(next.isEmpty())Text("No upcoming guide data for the sampled favorites.",color=TvPalette.Muted)else LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){items(next){(id,_,p)->val c=channels.find{it.id==id};DiscoverCard(p.title,(c?.name?:"")+" · "+java.time.Instant.ofEpochMilli(p.start).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("EEE HH:mm")),metadata[programmeMetadataKey(p)]){state.selected.value="all";state.query.value="";state.start.longValue=p.start;state.returnChannel.value=id;state.focusedChannel.value=c;state.focusedProgramme.value=p;guide()}}}
  }
  item{Text("Recordings",style=MaterialTheme.typography.titleLarge)}
  item{if(recordings.isEmpty())Text("Your recordings will appear here.",color=TvPalette.Muted)else LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){items(recordings){(title,status)->DiscoverCard(title,status+" · Open recordings",null){library()}}}}
  item{Text(notice,color=TvPalette.Muted,style=MaterialTheme.typography.bodySmall)}
 }
}
@Composable private fun DiscoverCard(title:String,subtitle:String,data:org.json.JSONObject?,click:()->Unit){
 var focused by remember{mutableStateOf(false)}
 OutlinedCard(onClick=click,modifier=Modifier.width(220.dp).height(190.dp).onFocusChanged{focused=it.isFocused},border=BorderStroke(if(focused)3.dp else 1.dp,if(focused)TvPalette.Accent else TvPalette.GuideBorder)){
  Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
   val art=if(Appearance.current.artwork!="off")metadataArtwork(data)else null
   if(art!=null)MetadataArtwork(art,Modifier.fillMaxWidth().height(88.dp))
   Text(title,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium)
   Text(subtitle,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
  }
 }
}
