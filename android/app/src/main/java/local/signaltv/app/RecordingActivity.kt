package local.signaltv.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class RecordingEntry(val id:String,val title:String,val subtitle:String,val status:String,val bytes:Long,val local:LocalRecording?=null,val resume:Double=0.0)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RecordingActivity:ComponentActivity(){
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 private var storage by mutableStateOf<String?>(null)
 private var notice by mutableStateOf("")
 private val folder=registerForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->if(uri!=null)try{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION);RecordingStore.storage(this,uri.toString());storage=uri.toString()}catch(_:Exception){notice="Cannot retain access to this folder. Choose a writable USB folder."}}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);storage=RecordingStore.storage(this);setContent{MaterialTheme(colorScheme=darkColorScheme(primary=TvPalette.Accent,onPrimary=TvPalette.OnAccent,background=TvPalette.Background,surface=TvPalette.Surface)){Surface(Modifier.fillMaxSize()){Content()}}}}
 override fun onDestroy(){scope.cancel();super.onDestroy()}
 private suspend fun api(p:Profile,path:String,method:String="GET",body:JSONObject?=null)=request(p.base+"/api/v1/recordings"+path,p.token,method,body)
 private fun work(action:suspend()->Unit){scope.launch{try{action()}catch(e:CancellationException){throw e}catch(e:Exception){notice=e.message?:"Recording operation failed"}}}
 @Composable private fun Content(){
  val p=remember{SecureProfile(this).accounts().find{it.accountKey()==intent.getStringExtra("account")}}
  val server=p?.mode=="server"
  var entries by remember{mutableStateOf<List<RecordingEntry>>(emptyList())}
  var scheduling by remember{mutableStateOf(intent.getBooleanExtra("schedule",false))}
  var playing by remember{mutableStateOf<RecordingEntry?>(null)}
  var deleting by remember{mutableStateOf<RecordingEntry?>(null)}
  var revision by remember{mutableIntStateOf(0)}
  suspend fun refresh(){entries=withContext(Dispatchers.IO){if(server){val a=JSONArray(api(p,""));(0 until a.length()).map{a.getJSONObject(it).let{j->RecordingEntry(j.getString("id"),j.getString("title"),j.getJSONObject("channel").getString("name")+" · "+Instant.parse(j.getString("start")).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))+" · "+j.optString("reason")+(if(j.optInt("gaps")>0)" · ${j.optInt("gaps")} interruption(s)" else ""),j.getString("status"),j.optLong("bytes"),resume=j.optDouble("resume"))}}}else RecordingStore.list(this@RecordingActivity).map{RecordingEntry(it.id,it.title,it.channelName+" · "+Instant.ofEpochMilli(it.start).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))+" · "+it.reason+if(it.gaps>0)" · ${it.gaps} interruption(s)" else "",it.status,it.bytes,it)}}.reversed()}
  LaunchedEffect(revision){while(true){try{refresh()}catch(e:CancellationException){throw e}catch(_:Exception){notice="Could not refresh recordings"};delay(4000)}}
  Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){TextButton(onClick={finish()}){Text("Back to guide")};Text("RECORDINGS",style=MaterialTheme.typography.headlineSmall);Button(onClick={scheduling=true},enabled=p!=null){Text("Schedule recording")}}
   Text(if(server)"Recorded on your server · continues when Android disconnects" else "Recorded on this Android device · no server required")
   if(!server){Text("Storage: "+(storage?.let{if(it=="internal")"Internal app storage" else "Selected USB / document folder"}?:"Choose a location before recording"));Row{TextButton(onClick={RecordingStore.storage(this@RecordingActivity,"internal");storage="internal"}){Text("Use internal storage")};TextButton(onClick={folder.launch(null)}){Text("Choose USB / folder")};if(!RecordingStore.exactAllowed(this@RecordingActivity))TextButton(onClick={startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName")))}){Text("Allow scheduled recordings")}}
    Text("USB is recommended. Existing schedules keep their chosen folder. Standalone recording uses your provider's MPEG-TS endpoint; the device must remain powered on.",style=MaterialTheme.typography.bodySmall)}
   if(notice.isNotBlank())Text(notice,color=MaterialTheme.colorScheme.primary)
   if(entries.isEmpty())Text("No recordings yet")
   entries.forEach{entry->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
    Text((if(entry.status=="recording")"🔴 " else "")+entry.title,style=MaterialTheme.typography.titleMedium);Text(entry.subtitle);Text("${entry.status} · ${entry.bytes/1048576} MiB")
    Row{if(entry.status in listOf("scheduled","recording","stopping"))TextButton(onClick={work{if(server)api(p,"/${entry.id}/cancel","POST",JSONObject())else withContext(Dispatchers.IO){RecordingStore.cancel(this@RecordingActivity,entry.id)};revision++}},enabled=entry.status!="stopping"){Text("Stop / cancel")}
     else{if(entry.bytes>0)TextButton(onClick={playing=entry}){Text("Play / resume")};TextButton(onClick={deleting=entry}){Text("Delete")}}}
   }}}
  }
  if(deleting!=null)AlertDialog(onDismissRequest={deleting=null},title={Text("Delete recording?")},text={Text("This permanently removes the selected recording files.")},confirmButton={Button(onClick={val entry=deleting!!;deleting=null;work{if(server)api(p,"/${entry.id}","DELETE")else withContext(Dispatchers.IO){RecordingStore.delete(this@RecordingActivity,entry.id)};revision++}}){Text("Delete")}},dismissButton={TextButton(onClick={deleting=null}){Text("Keep")}})
  if(scheduling&&p!=null)Schedule(p,onClose={scheduling=false},onSaved={scheduling=false;revision++})
  playing?.let{entry->Playback(entry,p){playing=null;revision++}}
 }
 @Composable private fun Schedule(p:Profile,onClose:()->Unit,onSaved:()->Unit){
  var channels by remember{mutableStateOf<List<Channel>>(emptyList())};var selected by remember{mutableStateOf<Channel?>(null)};var expanded by remember{mutableStateOf(false)}
  val formatter=remember{DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")};fun local(ms:Long)=Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(formatter)
  var title by remember{mutableStateOf(intent.getStringExtra("title").orEmpty())};var start by remember{mutableStateOf(local(maxOf(System.currentTimeMillis(),intent.getLongExtra("start",0))))};var end by remember{mutableStateOf(local(intent.getLongExtra("end",System.currentTimeMillis()+3600000)))}
  var before by remember{mutableStateOf("1")};var after by remember{mutableStateOf("3")};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
  var limit by remember{mutableIntStateOf(1)}
  LaunchedEffect(p){try{val repo=if(p.mode=="server")ServerRepository(p)else XtreamRepository(p,this@RecordingActivity);channels=repo.channels();limit=repo.connectionLimit;selected=channels.find{it.id==intent.getStringExtra("channel")}?:channels.firstOrNull()}catch(e:CancellationException){throw e}catch(_:Exception){error="Could not load channels. Check the saved provider connection."}}
  Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)){Surface(Modifier.fillMaxWidth(.9f).fillMaxHeight(.94f),shape=MaterialTheme.shapes.large){Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Text("Schedule recording",style=MaterialTheme.typography.headlineSmall)
   OutlinedButton(onClick={expanded=!expanded}){Text(selected?.name?:"Loading channels…")}
   if(expanded){var search by remember{mutableStateOf("")};TvTextInput(search,{search=it},label={Text("Find channel")});channels.filter{it.name.contains(search,true)}.take(40).forEach{c->TextButton(onClick={selected=c;expanded=false}){Text(c.name)}}}
   TvTextInput(title,{title=it},label={Text("Recording title")});TvTextInput(start,{start=it},label={Text("Start · yyyy-MM-dd HH:mm (local time)")});TvTextInput(end,{end=it},label={Text("End · yyyy-MM-dd HH:mm (local time)")});Row{TvTextInput(before,{before=it},Modifier.weight(1f),label={Text("Minutes before")});TvTextInput(after,{after=it},Modifier.weight(1f),label={Text("Minutes after")})}
   if(error.isNotEmpty())Text(error)
   Row{Button(enabled=!busy&&selected!=null,onClick={busy=true;scope.launch{try{val c=selected!!;val begin=LocalDateTime.parse(start,formatter).atZone(ZoneId.systemDefault()).toInstant();val finish=LocalDateTime.parse(end,formatter).atZone(ZoneId.systemDefault()).toInstant();val pre=before.toInt();val post=after.toInt();require(pre in 0..60&&post in 0..120&&finish.isAfter(begin)&&java.time.Duration.between(begin,finish).toHours()<24){"Check times and padding (up to 24 hours)"};if(p.mode=="server")api(p,"","POST",JSONObject().put("channelId",c.id).put("title",title.ifBlank{c.name}).put("start",begin.toString()).put("end",finish.toString()).put("beforeMinutes",pre).put("afterMinutes",post))else{withContext(Dispatchers.IO){RecordingStore.add(this@RecordingActivity,p,c,title.ifBlank{c.name},begin.toEpochMilli()-pre*60000L,finish.toEpochMilli()+post*60000L,limit)};if(begin.toEpochMilli()-pre*60000L<=System.currentTimeMillis())startForegroundService(Intent(this@RecordingActivity,RecordingService::class.java))};notice="Recording scheduled";onSaved()}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"Could not schedule recording"}finally{busy=false}}}){Text("Save recording")};TextButton(onClick=onClose){Text("Cancel")}}
  }}}
 }
 @Composable private fun Playback(entry:RecordingEntry,p:Profile?,close:()->Unit){
  val player=remember(entry.id){val http=DefaultHttpDataSource.Factory().apply{if(entry.local==null&&p!=null)setDefaultRequestProperties(mapOf("Authorization" to "Bearer ${p.token}"))};ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this,http))).build().apply{if(entry.local!=null){setMediaItems(entry.local.parts.map{MediaItem.fromUri(it)},entry.local.resumePart.coerceIn(0,(entry.local.parts.size-1).coerceAtLeast(0)),entry.local.resumeMs)}else setMediaItem(MediaItem.fromUri(p!!.base+"/recorded/${entry.id}/recording.m3u8"),(entry.resume*1000).toLong());prepare();playWhenReady=true}}
  DisposableEffect(player){onDispose{val index=player.currentMediaItemIndex;val pos=player.currentPosition.coerceAtLeast(0);player.release();work{if(entry.local!=null)withContext(Dispatchers.IO){RecordingStore.update(this@RecordingActivity,entry.id){it.copy(resumePart=index.coerceAtLeast(0),resumeMs=pos)}}else if(p!=null)api(p,"/${entry.id}/resume","PUT",JSONObject().put("seconds",pos/1000.0))}}}
  Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)){Surface(Modifier.fillMaxSize()){Column{TextButton(onClick=close){Text("Back to recordings")};AndroidView(factory={PlayerView(it).apply{this.player=player;useController=true;keepScreenOn=true}},modifier=Modifier.weight(1f).fillMaxWidth())}}}
 }
}
