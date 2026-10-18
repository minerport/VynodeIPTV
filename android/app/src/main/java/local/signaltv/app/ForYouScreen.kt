package local.signaltv.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun TeamEmblem(t:InterestTeam){
 Row(Modifier.background(Color(0xfff0f2f5),RoundedCornerShape(10.dp)).padding(6.dp)){
  ChannelLogo(t.logo,Modifier.size(48.dp));Text(t.abbreviation,color=Color(0xff18202c),modifier=Modifier.padding(6.dp))
 }
}
@Composable fun ForYouScreen(channels:List<Channel>,repo:Repository,state:GuideUiState,guide:()->Unit,browse:()->Unit,play:(Channel)->Unit){
 val context=LocalContext.current;val catalog=remember{InterestCatalog.load(context)};val store=remember(repo.profile){InterestStore(context,repo.profile)}
 var settings by remember(repo.profile){mutableStateOf(InterestSettings())};var hits by remember(repo.profile){mutableStateOf(emptyList<InterestHit>())};var error by remember{mutableStateOf("")};var loaded by remember{mutableStateOf(false)};var editing by remember{mutableStateOf(false)};var revision by remember{mutableIntStateOf(0)};var now by remember{mutableLongStateOf(System.currentTimeMillis())}
 LaunchedEffect(store,revision){while(true){try{settings=store.load();hits=store.hits(channels,state.cache.mapValues{it.value.second},catalog);error="";loaded=true}catch(e:CancellationException){throw e}catch(_:Exception){error="Personal picks unavailable. Check your connection and server version."};now=System.currentTimeMillis();delay(60000)}}
 BackHandler{guide()}
 LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(14.dp)){
  item{Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xff202b3c),Color(0xff17191e))),RoundedCornerShape(16.dp)).padding(12.dp)){
   Text("YOUR TEAMS. YOUR TOWN. YOUR TV.",color=Color(0xffd7c49b),style=MaterialTheme.typography.labelLarge)
   Row{Text("For you",style=MaterialTheme.typography.headlineMedium,color=Color.White,modifier=Modifier.weight(1f));TextButton(onClick=guide){Text("Guide",color=Color.White)};TextButton(onClick=browse){Text("Collection",color=Color.White)};Button(onClick={editing=true},enabled=loaded){Text("Edit interests")}}
   Text("Live now and the next seven days, from your channel lineup.",color=Color.White,style=MaterialTheme.typography.bodySmall)
   LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){items(catalog.teams.filter{it.id in settings.teams},key={it.id}){t->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){TeamEmblem(t);Text(t.name,color=Color.White,modifier=Modifier.padding(top=16.dp))}}}
  }}
  if(!loaded)item{Text(if(error.isBlank())"Loading your picks…" else error);TextButton(onClick={revision++}){Text("Retry")}}
  if(loaded){
   if(error.isNotBlank())item{Text(error)}
   if(settings.teams.isEmpty()&&settings.genres.isEmpty()&&settings.city.isBlank()&&settings.localChannels.isEmpty())item{Text("Choose Edit interests to add teams, your hometown and favorite genres.")}
   for((section,title) in listOf("sports" to "Your teams","local" to "Around home","genres" to "Your genres")){
    item{Text(title,style=MaterialTheme.typography.headlineSmall)}
    for(live in listOf(true,false)){
     item{Text(if(live)"On now" else "Coming up",style=MaterialTheme.typography.titleMedium)}
     val matches=hits.filter{it.section==section&&it.programme.end>now&&(it.programme.start<=now)==live}
     item{if(matches.isEmpty())Text(if(section=="local")"No matching EPG listings. Select local stations in Edit interests when city/state is missing from their names." else "No matching EPG listings. Refresh EPG or change your interests.",color=TvPalette.Muted)else LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){
      items(matches,key={it.section+it.channel.id+it.programme.start}){h->val t=catalog.teams.find{it.id==h.teamId};var focused by remember{mutableStateOf(false)};val accent=t?.color?.let{runCatching{Color(android.graphics.Color.parseColor("#"+it))}.getOrNull()}?:TvPalette.Accent
       OutlinedCard(onClick={if(live)play(h.channel)else{state.selected.value="all";state.query.value="";state.start.longValue=maxOf(System.currentTimeMillis(),h.programme.start);state.returnChannel.value=h.channel.id;state.focusedChannel.value=h.channel;state.focusedProgramme.value=h.programme;guide()}},modifier=Modifier.width(270.dp).height(230.dp).onFocusChanged{focused=it.isFocused},border=BorderStroke(if(focused)4.dp else 2.dp,if(focused)TvPalette.Accent else accent)){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){if(t!=null)TeamEmblem(t);Text(if(live)"ON NOW · WATCH" else "COMING UP · OPEN GUIDE",style=MaterialTheme.typography.labelSmall);Text(h.programme.title,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium);Text(h.channel.name,maxLines=1);Text(Instant.ofEpochMilli(h.programme.start).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE HH:mm")),style=MaterialTheme.typography.bodySmall);Text(h.reason,maxLines=2,style=MaterialTheme.typography.bodySmall)}
       }
      }
     }}
    }
   }
   item{Text("Matches use provider EPG and may include replays or studio shows. Up to 40 listings per section. Emblems: ESPN; marks belong to their teams.",color=TvPalette.Muted,style=MaterialTheme.typography.bodySmall)}
  }
 }
 if(editing)InterestEditor(settings,catalog,channels,store,{editing=false},{editing=false;revision++})
}
@Composable private fun InterestEditor(initial:InterestSettings,catalog:InterestCatalog,channels:List<Channel>,store:InterestStore,dismiss:()->Unit,saved:()->Unit){
 var draft by remember{mutableStateOf(initial)};var tab by remember{mutableStateOf("Teams")};var query by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var notice by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
 AlertDialog(onDismissRequest={if(!busy)dismiss()},title={Text("Make it yours")},text={Column{
  Row{for(name in listOf("Teams","Location","Genres"))TextButton(onClick={tab=name;query=""},enabled=!busy){Text(name)}}
  if(tab=="Location"){TvTextInput(draft.city,{draft=draft.copy(city=it.take(80))},label={Text("City")},singleLine=true);TvTextInput(draft.state,{draft=draft.copy(state=it.take(80))},label={Text("State name or abbreviation")},singleLine=true);Text("Choose stations below when provider names omit your location.",style=MaterialTheme.typography.bodySmall)}
  TvTextInput(query,{query=it.take(100)},label={Text(if(tab=="Location")"Find local stations" else "Find "+tab.lowercase())},singleLine=true)
  LazyColumn(Modifier.height(230.dp)){
   if(tab=="Teams")items(catalog.teams.filter{(it.name+" "+it.league).contains(query,true)},key={it.id}){t->Row(Modifier.fillMaxWidth().padding(4.dp)){Checkbox(modifier=Modifier.semantics{contentDescription=t.name+" "+t.league},checked=t.id in draft.teams,enabled=!busy,onCheckedChange={checked->if(!checked||draft.teams.size<30)draft=draft.copy(teams=if(checked)draft.teams+t.id else draft.teams-t.id)else notice="Choose up to 30 teams"});Column{Text(t.name);Text(t.league,style=MaterialTheme.typography.bodySmall)}}}
   if(tab=="Genres")items(catalog.genres.keys.filter{it.contains(query,true)},key={it}){g->Row{Checkbox(modifier=Modifier.semantics{contentDescription=g},checked=g in draft.genres,enabled=!busy,onCheckedChange={draft=draft.copy(genres=if(it)draft.genres+g else draft.genres-g)});Text(g)}}
   if(tab=="Location")items(channels.filter{it.name.contains(query,true)},key={it.id}){c->Row{Checkbox(modifier=Modifier.semantics{contentDescription=c.name},checked=c.id in draft.localChannels,enabled=!busy,onCheckedChange={checked->if(!checked||draft.localChannels.size<100)draft=draft.copy(localChannels=if(checked)draft.localChannels+c.id else draft.localChannels-c.id)else notice="Choose up to 100 stations"});Text(c.name)}}
  }
  Text(notice,style=MaterialTheme.typography.bodySmall)
 }},confirmButton={TextButton(enabled=!busy,onClick={busy=true;scope.launch{try{store.save(draft);saved()}catch(e:CancellationException){throw e}catch(_:Exception){notice="Could not save. Your previous interests are retained."}finally{busy=false}}}){Text(if(busy)"Saving…" else "Save")}},dismissButton={TextButton(enabled=!busy,onClick=dismiss){Text("Cancel")}})
}
