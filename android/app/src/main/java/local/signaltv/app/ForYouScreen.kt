package local.signaltv.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
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
 LaunchedEffect(Unit){while(true){now=System.currentTimeMillis();delay(15000)}}
 val sportsStore=remember(repo.profile){SportsStore(context,repo.profile)}
 var sports by remember(repo.profile){mutableStateOf<Map<String,SportsLeague>>(emptyMap())};var sportsErrors by remember{mutableStateOf(setOf<String>())}
 LaunchedEffect(settings.teams,sportsStore){sportsErrors=emptySet();val leagues=catalog.teams.filter{it.id in settings.teams}.map{it.league}.distinct();while(true){for(league in leagues){try{sports=sports+ (league to sportsStore.load(league));sportsErrors=sportsErrors-league}catch(e:CancellationException){throw e}catch(_:Exception){sports=sports-league;sportsErrors=sportsErrors+league}};delay(900000)}}
 var exploring by remember{mutableStateOf(false)};var sportsTab by remember{mutableStateOf("Games")}
 var gameData by remember(repo.profile){mutableStateOf<Map<String,List<SportsGame>>>(emptyMap())};var gameErrors by remember{mutableStateOf(setOf<String>())}
 LaunchedEffect(settings.teams,sportsStore){val leagues=catalog.teams.filter{it.id in settings.teams}.map{it.league}.distinct();while(true){for(l in leagues){try{val board=try{sportsGames(sportsStore.data("scoreboard",l),l)}catch(e:CancellationException){throw e}catch(_:Exception){emptyList()};val scheduled=mutableListOf<SportsGame>();var success=board.isNotEmpty();for(t in catalog.teams.filter{it.id in settings.teams&&it.league==l}){try{scheduled+=sportsGames(sportsStore.data("schedule",l,t.id),l,true);success=true}catch(e:CancellationException){throw e}catch(_:Exception){}};check(success);gameData=gameData+(l to sportsMerge(scheduled,board));gameErrors=gameErrors-l}catch(e:CancellationException){throw e}catch(_:Exception){gameErrors=gameErrors+l;gameData=gameData-l}};delay(60000)}}
 BackHandler{guide()}
 LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(14.dp)){
  item{Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xff202b3c),Color(0xff17191e))),RoundedCornerShape(16.dp)).padding(12.dp)){
   Text("YOUR TEAMS. YOUR TOWN. YOUR TV.",color=Color(0xffd7c49b),style=MaterialTheme.typography.labelLarge)
   Row{Text("For you",style=MaterialTheme.typography.headlineMedium,color=Color.White,modifier=Modifier.weight(1f));TextButton(onClick=guide){Text("Guide",color=Color.White)};TextButton(onClick=browse){Text("Collection",color=Color.White)};Button(onClick={editing=true},enabled=loaded){Text("Edit interests")}}
   Text("Your teams’ schedules, local channels and favorite genres.",color=Color.White,style=MaterialTheme.typography.bodySmall)
   LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){items(catalog.teams.filter{it.id in settings.teams},key={it.id}){t->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){TeamEmblem(t);Text(t.name,color=Color.White,modifier=Modifier.padding(top=16.dp))}}}
  }}
  if(!loaded)item{Text(if(error.isBlank())"Loading your picks…" else error);TextButton(onClick={revision++}){Text("Retry")}}
  if(loaded){
   if(error.isNotBlank())item{Text(error)}
   if(settings.teams.isEmpty()&&settings.genres.isEmpty()&&settings.city.isBlank()&&settings.localChannels.isEmpty())item{Text("Choose Edit interests to add teams, your hometown and favorite genres.")}
   item{LazyRow{items(listOf("Games","Teams","Standings","Players","Compare")){tab->TextButton(onClick={sportsTab=tab;exploring=true}){Text(tab)}}}}
   if(settings.teams.isNotEmpty())item{SportsOverview(catalog,settings.teams,sports,sportsErrors)}
   if(settings.teams.isNotEmpty())item{SportsGamesShelf(sportsTeamGames(gameData.values.flatten(),settings.teams,now).distinctBy{it.id},gameErrors,catalog,channels.filter{"provider:"+it.groupId !in state.preferences.value.hiddenGroups},hits,sports,now,play)}
   for((section,title) in listOf("local" to "Around home","genres" to "Your genres")){
    item{Text(title,style=MaterialTheme.typography.headlineSmall)}
    for(live in listOf(true,false)){
     item{Text(if(live)"On now" else "Coming up",style=MaterialTheme.typography.titleMedium)}
     val matches=hits.filter{it.section==section&&it.programme.end>now&&(it.programme.start<=now)==live}
     item{if(matches.isEmpty())Text(if(section=="local")"No matching EPG listings. Select local stations in Edit interests when city/state is missing from their names." else "No matching EPG listings. Refresh EPG or change your interests.",color=TvPalette.Muted)else LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){
      items(matches,key={it.section+it.channel.id+it.programme.start}){h->val t=catalog.teams.find{it.id==h.teamId};var focused by remember{mutableStateOf(false)};val accent=t?.color?.let{runCatching{Color(android.graphics.Color.parseColor("#"+it))}.getOrNull()}?:TvPalette.Accent
       OutlinedCard(onClick={if(live)play(h.channel)else{state.selected.value="all";state.query.value="";state.start.longValue=maxOf(System.currentTimeMillis(),h.programme.start);state.returnChannel.value=h.channel.id;state.focusedChannel.value=h.channel;state.focusedProgramme.value=h.programme;guide()}},modifier=Modifier.width(270.dp).height(if(t!=null)400.dp else 230.dp).onFocusChanged{focused=it.isFocused},border=BorderStroke(if(focused)4.dp else 2.dp,if(focused)TvPalette.Accent else accent)){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){if(t!=null)TeamEmblem(t);Text(if(live)"ON NOW · WATCH" else "COMING UP · OPEN GUIDE",style=MaterialTheme.typography.labelSmall);Text(h.programme.title,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium);Text(h.channel.name,maxLines=1);Text(Instant.ofEpochMilli(h.programme.start).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE HH:mm")),style=MaterialTheme.typography.bodySmall);Text(h.reason,maxLines=2,style=MaterialTheme.typography.bodySmall)
         if(t!=null){val league=sports[t.league];val own=league?.rows?.find{it.id==t.id};val opponent=sportsOpponent(h.programme.title,t,catalog);Text("Season stats"+(own?.let{" · "+it.season+" "+it.phase}?:""),style=MaterialTheme.typography.labelSmall);Text(t.abbreviation+": "+sportsCompact(own),maxLines=3,style=MaterialTheme.typography.bodySmall);Text(if(opponent!=null)opponent.abbreviation+": "+sportsCompact(league?.rows?.find{it.id==opponent.id})else "Opponent not identified in this listing",maxLines=3,style=MaterialTheme.typography.bodySmall)}
        }
       }
      }
     }}
    }
   }
   item{Text("Matches use provider EPG and may include replays or studio shows. Up to 40 listings per section. Emblems: ESPN; marks belong to their teams.",color=TvPalette.Muted,style=MaterialTheme.typography.bodySmall)}
  }
 }
 if(exploring)SportsExplorerScreen(sportsStore,catalog,settings.teams,sportsTab){exploring=false}
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

@Composable private fun SportsOverview(catalog:InterestCatalog,selected:List<String>,data:Map<String,SportsLeague>,errors:Set<String>){
 var detail by remember{mutableStateOf("")}
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
 Text("Sports center",style=MaterialTheme.typography.titleLarge)
 Text("Your teams · ESPN season records",style=MaterialTheme.typography.bodySmall)
 LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){
  items(catalog.teams.filter{it.id in selected},key={it.id}){t->OutlinedButton(onClick={detail=t.id}){Column(Modifier.width(230.dp)){Text(t.name);Text(data[t.league]?.rows?.find{it.id==t.id}?.stats?.find{it.key=="overall"}?.value?:"View statistics",maxLines=1,style=MaterialTheme.typography.bodySmall)}}}
 }
 Row{for(league in catalog.teams.filter{it.id in selected}.map{it.league}.distinct())TextButton(onClick={detail=league}){Text(league+" standings")}}
 if(errors.isNotEmpty())Text("Stats temporarily unavailable: "+errors.joinToString()+". Guide and playback remain available.",style=MaterialTheme.typography.bodySmall)
 }
 if(detail.isNotBlank()){val team=catalog.teams.find{it.id==detail};val league=data[team?.league?:detail];val rows=league?.rows.orEmpty().filter{team==null||it.id==team.id}
 AlertDialog(onDismissRequest={detail=""},title={Text(team?.name?:detail+" standings")},text={LazyColumn(Modifier.heightIn(max=380.dp)){
  item{Text(if(league==null)"Statistics unavailable" else (if(league.stale)"Cached · " else "Updated · ")+Instant.ofEpochMilli(league.updated).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d HH:mm"))+" · ESPN")}
  items(rows,key={it.id}){r->Column(Modifier.padding(vertical=10.dp).focusable()){Text(r.name,style=MaterialTheme.typography.titleMedium);Text(r.season+" · "+r.phase+" · "+r.group,style=MaterialTheme.typography.bodySmall);for(stat in r.stats)Row(Modifier.fillMaxWidth()){Text(stat.label,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall);Text(stat.value,style=MaterialTheme.typography.bodySmall)}}}
 }},confirmButton={TextButton(onClick={detail=""}){Text("Close")}})
 }
}

@Composable private fun SportsGamesShelf(games:List<SportsGame>,errors:Set<String>,catalog:InterestCatalog,channels:List<Channel>,hits:List<InterestHit>,stats:Map<String,SportsLeague>,now:Long,play:(Channel)->Unit){
 var chosen by remember{mutableStateOf<SportsGame?>(null)}
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
 Text("Your games",style=MaterialTheme.typography.titleLarge)
 Text("ESPN start times and status. Provider channels are possible viewing options, not confirmation of a live broadcast.",style=MaterialTheme.typography.bodySmall)
 if(errors.isNotEmpty())Text("Schedule unavailable: "+errors.joinToString()+". EPG slots are not treated as live games.")
 if(games.isEmpty()&&errors.isEmpty())Text("No games currently listed by ESPN for your teams.")
 for(upcoming in listOf(false,true)){
 Text(if(upcoming)"Coming up · all scheduled games" else "Live & recent",style=MaterialTheme.typography.titleMedium)
 val sectionGames=games.filter{(it.state=="pre"||it.start>now)==upcoming}
 if(sectionGames.isEmpty())Text(if(upcoming)"No upcoming games currently listed by ESPN." else "No live or recent games.")
 LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){items(sectionGames.sortedWith(compareBy<SportsGame>{!sportsLive(it,now)}.thenBy{it.start}),key={it.id}){g->OutlinedCard(onClick={chosen=g},modifier=Modifier.width(310.dp).heightIn(min=170.dp)){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
 Text(sportsStatus(g,now),style=MaterialTheme.typography.labelMedium)
 Text(if(g.timeValid)"ESPN · "+Instant.ofEpochMilli(g.start).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE MMM d HH:mm"))else "Start time TBD")
 for(t in g.teams){Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){catalog.teams.find{it.id==t.id}?.let{ChannelLogo(it.logo,Modifier.size(30.dp))};Text(t.name,Modifier.weight(1f).padding(horizontal=8.dp),maxLines=1,overflow=TextOverflow.Ellipsis);Text(if(g.state!="pre")t.score else stats[g.league]?.rows?.find{it.id==t.id}?.stats?.find{it.key=="overall"}?.value.orEmpty(),style=MaterialTheme.typography.labelMedium)}}
 Text("Select for possible channels",style=MaterialTheme.typography.labelSmall)
 }}}}
 }
 }
 chosen?.let{g->val options=sportsChannels(g,channels,hits,catalog);AlertDialog(onDismissRequest={chosen=null},title={Text(g.name)},text={Column{Text(sportsStatus(g,now)+" · ESPN");Text("Channel availability is unverified. A team channel may not carry this game.",style=MaterialTheme.typography.bodySmall);if(options.isEmpty())Text("No matching provider channel found.");LazyColumn(Modifier.heightIn(max=280.dp)){items(options,key={it.id}){c->TextButton(onClick={chosen=null;play(c)}){Text("Open channel · "+c.name)}}}}},confirmButton={TextButton(onClick={chosen=null}){Text("Close")}})}
}
