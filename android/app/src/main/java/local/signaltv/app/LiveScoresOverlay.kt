package local.signaltv.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject

@Stable class LiveScoreState(context:Context,profile:Profile){
 private val storage=context.getSharedPreferences("live-score-overlay",Context.MODE_PRIVATE);private val key=profile.accountKey()
 var prefs by mutableStateOf(runCatching{ScorePreferences.parse(JSONObject(storage.getString(key,"{}")!!))}.getOrDefault(ScorePreferences()));private set
 var editing by mutableStateOf(false);var page by mutableIntStateOf(0)
 fun save(p:ScorePreferences){prefs=ScorePreferences.parse(p.json());storage.edit().putString(key,prefs.json().toString()).apply();page=0}
 fun toggle(){save(prefs.copy(visible=!prefs.visible))}
}
@Composable fun BoxScope.LiveScoreLayer(profile:Profile,channel:Channel,title:String,state:LiveScoreState,controlsVisible:Boolean){
 val context=LocalContext.current;val catalog=remember{InterestCatalog.load(context)};val store=remember(profile){SportsStore(context,profile)};val history=remember(profile){ScoreHistory()};var unavailable by remember{mutableStateOf(emptyList<String>())};var loading by remember{mutableStateOf(false)};var now by remember{mutableLongStateOf(System.currentTimeMillis())}
 val p=state.prefs;val active=p.visible||state.editing
 LaunchedEffect(active,store){if(active)while(true){loading=true;val limit=Semaphore(3);val results=coroutineScope{sportsPaths.keys.map{league->async{limit.withPermit{try{league to withContext(Dispatchers.Default){val payload=store.data("scoreboard",league);check(!payload.stale);sportsGames(payload,league)}}catch(e:CancellationException){throw e}catch(_:Exception){league to null}}}}.awaitAll()};unavailable=results.filter{it.second==null}.map{it.first};for((l,data)in results)if(data!=null)history.add(l,data,System.currentTimeMillis());now=System.currentTimeMillis();loading=false;delay(15000)}}
 LaunchedEffect(active,p.rotate,state.editing){if(active){var tick=0;while(true){delay(3000);now=System.currentTimeMillis();tick++;if(tick%4==0&&p.rotate&&!state.editing)state.page++}}}
 val games=history.games(p.delay,now);val effective=now-p.delay*1000L
 val league=if(p.automatic)scoreContext(channel,title,catalog)?:p.league else p.league
 val rowLimit=minOf(p.rows,((LocalConfiguration.current.screenHeightDp*.8f-50)/(if(p.lastPlay)120 else 85)).toInt().coerceAtLeast(1));val list=scoreGames(games,p,league,title,catalog,effective);val pages=scorePageCount(list.size,rowLimit);val page=((state.page%pages)+pages)%pages
 val accent=when(p.accent){"gold"->Color(0xffe3ba70);"ice"->Color(0xff8bd9f5);else->Color(0xffef3448)}
 val alignment=when(p.position){"top-left"->Alignment.TopStart;"top-center"->Alignment.TopCenter;"top-right"->Alignment.TopEnd;"bottom-center"->Alignment.BottomCenter;"bottom-right"->Alignment.BottomEnd;else->Alignment.BottomStart}
 // The score bug is read-only: it never steals the remote focus from playback.
 if(p.visible&&!controlsVisible){Column(Modifier.align(alignment).padding(18.dp).width(if(p.size=="large")260.dp else 205.dp).background(Color(0xff0c0f14).copy(alpha=p.opacity/100f),RoundedCornerShape(4.dp)).border(1.dp,Color.White.copy(alpha=.18f),RoundedCornerShape(4.dp))){
  Box(Modifier.fillMaxWidth().height(3.dp).background(accent))
  Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=5.dp)){Text(if(p.mode=="sport")"$league SCORES" else if(p.mode=="pinned")"PINNED SCORES" else "SCORES",color=accent,fontSize=10.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));if(pages>1)Text("${page+1}/$pages",color=Color.LightGray,fontSize=10.sp)}
  for(g in list.drop(page*rowLimit).take(rowLimit)){Column(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=5.dp)){for(t in g.teams){val team=catalog.teams.find{it.id==t.id};Row(verticalAlignment=Alignment.CenterVertically){team?.let{ChannelLogo(it.logo,Modifier.size(if(p.size=="large")24.dp else 19.dp))};Text((if(g.league=="NFL"&&g.possession==t.id)"◆ " else "")+(team?.abbreviation?:t.name),Modifier.weight(1f).padding(start=6.dp),fontSize=if(p.size=="large")16.sp else 13.sp,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold);Text(t.score.ifBlank{"—"},fontSize=if(p.size=="large")18.sp else 15.sp,color=Color.White,fontWeight=FontWeight.Bold)}};Text(g.league+" · "+g.detail,color=Color(0xffbfc9d6),fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis);if(g.league=="NFL"&&g.downDistance.isNotBlank())Text(g.downDistance,color=accent,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis);if(p.lastPlay&&g.lastPlay.isNotBlank())Text("Last play · "+g.lastPlay,color=Color.White,fontSize=10.sp,maxLines=2,overflow=TextOverflow.Ellipsis);Text(scoreAge(g.updated,now),color=Color.LightGray,fontSize=9.sp);if(!sportsLive(g,effective)||g.league in unavailable)Text("Update delayed · last known score",color=accent,fontSize=9.sp)};HorizontalDivider(color=Color.White.copy(alpha=.1f))}
  if(p.delay>0)Text("${p.delay}s spoiler delay",Modifier.padding(horizontal=10.dp),color=accent,fontSize=9.sp)
  if(list.isEmpty())Text(if(unavailable.isNotEmpty())"Scores unavailable · retrying" else if(!history.ready(p.delay,now))(if(p.delay>0)"Building delay buffer…" else "Checking games…") else if(games.any{it.stale||effective-it.updated>90000})"Scores unavailable · retrying" else "No other live games",Modifier.padding(10.dp),color=Color.LightGray,fontSize=11.sp)
 }}
 if(state.editing){Dialog(onDismissRequest={state.editing=false},properties=DialogProperties(usePlatformDefaultWidth=false)){
 Box(Modifier.fillMaxSize()){
 Column(Modifier.align(Alignment.CenterEnd).padding(12.dp).width(380.dp).fillMaxHeight().background(Color(0xf514191f),RoundedCornerShape(10.dp)).padding(14.dp)){
 Row(verticalAlignment=Alignment.CenterVertically){Text("Live scores",Modifier.weight(1f),color=Color.White,style=MaterialTheme.typography.titleLarge);TextButton(onClick={state.editing=false}){Text("Done")}}
 Text("↑ Show / hide while watching · Pins never change your channel",color=Color.LightGray,style=MaterialTheme.typography.labelSmall)
 LazyColumn(Modifier.weight(1f)){
 item{Row{TextButton(onClick=state::toggle){Text(if(p.visible)"Hide overlay" else "Show overlay")};TextButton(onClick={state.page++}){Text("Next page (${page+1}/$pages)")}}}
 item{SportsChoice("Games",p.mode,listOf("sport" to "Same sport","all" to "All live sports","pinned" to "Pinned games")){state.save(p.copy(mode=it))}}
 item{SportsChoice("Sport",if(p.automatic)"auto" else p.league,listOf("auto" to "Automatic ($league)")+sportsPaths.keys.map{it to it}){state.save(p.copy(automatic=it=="auto",league=if(it=="auto")p.league else it))}}
 item{SportsChoice("Position",p.position,scorePositions.map{it to it.replace('-',' ')}){state.save(p.copy(position=it))}}
 item{Row{SportsChoice("Text",p.size,listOf("compact" to "Compact","large" to "Larger")){state.save(p.copy(size=it))};SportsChoice("Background",p.opacity.toString(),listOf(60,80,95).map{it.toString() to "$it%"}){state.save(p.copy(opacity=it.toInt()))}}}
 item{Row{SportsChoice("Accent",p.accent,listOf("red" to "Red","gold" to "Gold","ice" to "Ice")){state.save(p.copy(accent=it))};SportsChoice("Rows",p.rows.toString(),listOf(1,3,5).map{it.toString() to it.toString()}){state.save(p.copy(rows=it.toInt()))}}}
 item{SportsChoice("Last play",p.lastPlay.toString(),listOf("true" to "Show","false" to "Hide")){state.save(p.copy(lastPlay=it.toBoolean()))}}
 item{SportsChoice("Spoiler delay",p.delay.toString(),listOf(0,15,30,60,90,120).map{it.toString() to if(it==0)"Off" else "$it seconds"}){state.save(p.copy(delay=it.toInt()))}}
 item{SportsChoice("Page rotation",p.rotate.toString(),listOf("false" to "Manual","true" to "Every 12 seconds")){state.save(p.copy(rotate=it.toBoolean()))}}
 item{Text("PIN LIVE GAMES · ${p.pins.size}/12",color=accent,style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(top=12.dp));Text(if(loading)"Checking ESPN…" else "${games.count{sportsLive(it,effective)}} live games · ESPN"+(if(unavailable.isEmpty())"" else " · Unavailable: "+unavailable.joinToString()),color=Color.LightGray,style=MaterialTheme.typography.labelSmall)}
 items(games.filter{sportsLive(it,effective)}.sortedWith(compareBy<SportsGame>{it.league}.thenBy{it.start}),key={it.id}){g->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=g.id in p.pins,onCheckedChange={checked->if(!checked||p.pins.size<12)state.save(p.copy(visible=true,mode="pinned",pins=if(checked)p.pins+g.id else p.pins-g.id))},enabled=g.id in p.pins||p.pins.size<12);Column{Text(g.teams.joinToString(" — "){t->(catalog.teams.find{it.id==t.id}?.abbreviation?:t.name)+" "+t.score},color=Color.White,fontSize=13.sp);Text(g.league+" · "+g.detail,color=Color.LightGray,fontSize=11.sp)}}}
 if(games.none{sportsLive(it,effective)})item{Text("No live games reported. Scheduled, finished and stale games are excluded.",color=Color.LightGray,style=MaterialTheme.typography.bodySmall)}
 if(p.pins.isNotEmpty())item{TextButton(onClick={state.save(p.copy(pins=emptyList()))}){Text("Clear pins")}}
 }
 }
 }
 }}
}
