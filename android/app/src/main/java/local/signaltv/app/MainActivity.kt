package local.signaltv.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.foundation.border
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.*
import org.json.JSONObject

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity: ComponentActivity() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private lateinit var secure: SecureProfile
    private var profile by mutableStateOf<Profile?>(null)
    private var channels by mutableStateOf<List<Channel>>(emptyList())
    private var status by mutableStateOf("Choose how to connect")
    private var current by mutableStateOf<Channel?>(null)
    private var fullScreen by mutableStateOf(false)
    private var guide by mutableStateOf("")
    private val slots=mutableStateListOf<Channel?>(null,null,null,null,null,null)
    private val engines=mutableStateMapOf<Int,PlaybackEngine>()
    private val slotStatus=mutableStateMapOf<Int,String>()
    private var selectedSlot by mutableIntStateOf(0)
    private var layout by mutableIntStateOf(2)
    private var multiView by mutableStateOf(false)
    private var wallMode by mutableStateOf(WallMode())
    private val multiEnabled get()=wallMode.enabled
    private var unlockError by mutableStateOf(false)
    private val engine:PlaybackEngine? get()=engines[selectedSlot]
    private val guideState=GuideUiState()
    private var backgrounded=false
    private var sceneLoaded=false
    private var closing: Job?=null
    private val requests=SlotRequests()
    private var repository by mutableStateOf<Repository?>(null)
    private var connectJob: Job?=null
    private var busy by mutableStateOf(false)
    private var healthOpen by mutableStateOf(false)
    private var catalogMetrics by mutableStateOf("Catalog measurements pending")
    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState);secure=SecureProfile(this)
        restoreConnection()
        setContent{MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFFDCE6F2),onPrimary=Color(0xFF18202B),background=Color(0xFF101216),surface=Color(0xFF20242B))){Surface(Modifier.fillMaxSize()){App()}}}
    }
    private fun restoreConnection(){try{unlockError=false;secure.load()?.let{profile=it;connect(it)}}catch(e:Exception){unlockError=true;status=e.message?:"Unable to unlock connection"}}
    private fun connect(p: Profile){
        connectJob?.cancel()
        val repo=repository?.takeIf{it.profile==p}?:if(p.mode=="server")ServerRepository(p)else XtreamRepository(p)
        repository=repo
        connectJob=scope.launch{val started=android.os.SystemClock.elapsedRealtime();busy=true;try{status="Loading channels…";if(channels.isEmpty())channels=withContext(Dispatchers.IO){CatalogCache(this@MainActivity,p).load()};catalogMetrics="Cached catalog: ${android.os.SystemClock.elapsedRealtime()-started}ms · ${channels.size} channels";val result=withContext(Dispatchers.IO){repo.channels().also{CatalogCache(this@MainActivity,p).save(it)}};secure.save(p);profile=p;channels=result;catalogMetrics+=" · refreshed: ${android.os.SystemClock.elapsedRealtime()-started}ms";restoreScene();status="${result.size} channels · ${if(p.mode=="server")"Companion" else "Standalone"}"}catch(e:CancellationException){throw e}catch(e:Exception){status=e.message?:"Connection failed"}finally{busy=false}}
    }
    private fun saveScene(){profile?.let{SceneStore(this,it).save(slots.toList(),layout,selectedSlot,multiEnabled)}}
    private fun restoreScene(){
        if(sceneLoaded)return;sceneLoaded=true
        profile?.let{SceneStore(this,it).load()}?.let{saved->
            val byId=channels.associateBy{it.id};saved.ids.forEachIndexed{i,id->slots[i]=byId[id]}
            layout=saved.layout;selectedSlot=saved.selected;current=slots[selectedSlot];wallMode=WallMode.restore(saved.wallEnabled,slots.count{it!=null})
            slots.forEachIndexed{i,c->if(c!=null)slotStatus[i]="Retained · select Multiview to resume"}
        }
    }
    private fun ensureStreams(){
        val repo=repository?:return;val epoch=requests.epoch
        scope.launch{closing?.join();if(backgrounded || epoch!=requests.epoch)return@launch
            (listOf(selectedSlot)+slots.indices.filter{it!=selectedSlot}).forEach{i->slots[i]?.let{c->
                if(engines.containsKey(i))return@let
                if(engines.size<repo.connectionLimit)startSlot(i,c,repo) else slotStatus[i]="Provider connection limit reached"
            }}
        }
    }
    private fun audio(){routePanelAudio(engines,selectedSlot){e,volume->e.setAudible(volume>0f)}}
    private fun trackClose(job:Job){val previous=closing;closing=scope.launch{previous?.join();job.join()}}
    private fun stopPlayback(clearSaved:Boolean=true){
        requests.suspendAll();val jobs=engines.values.map{it.close()};trackClose(scope.launch{jobs.joinAll()});engines.clear()
        for(i in slots.indices)slots[i]=null
        current=null;fullScreen=false;multiView=false;wallMode=wallMode.exit();selectedSlot=0;layout=2;slotStatus.clear()
        if(clearSaved)saveScene() else sceneLoaded=false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    private fun removeSlot(index:Int){
        requests.remove(index);engines.remove(index)?.let{trackClose(it.close())};slots[index]=null;slotStatus.remove(index)
        if(slots.none{it!=null}){stopPlayback();return}
        if(index==selectedSlot){selectSlot(slots.indexOfFirst{it!=null});fullScreen=false}
        saveScene()
    }

    private fun startSlot(index:Int,c:Channel,repo:Repository){
        if(backgrounded || engines.containsKey(index) || slots[index]?.id!=c.id)return
        slotStatus[index]="Opening…"
        engines[index]=PlaybackEngine(this,repo){slotStatus[index]=it;if(index==selectedSlot)status=it}.also{it.setAudible(false);it.start(c)}
        audio()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    private fun selectSlot(index:Int){selectedSlot=index;current=slots[index];status=slotStatus[index].orEmpty();guide=guideState.cache[current?.id]?.second?.firstOrNull()?.title.orEmpty();audio();saveScene()}
    private fun play(c: Channel){
        val repo=repository?:return
        val existing=slots.indexOfFirst{it?.id==c.id}
        if(existing>=0){selectSlot(existing);ensureStreams();fullScreen=true;multiView=false;return}
        if(slots[selectedSlot]==null && slots.count{it!=null}>=repo.connectionLimit){status="Provider allows ${repo.connectionLimit} simultaneous connections. Remove a panel first.";return}
        val index=selectedSlot;val previous=engines.remove(index)?.close();previous?.let{trackClose(it)};slots[index]=c;current=c;fullScreen=!multiEnabled;multiView=multiEnabled
        saveScene();val ticket=requests.begin(index)
        scope.launch{previous?.join();closing?.join();if(requests.accepts(ticket) && slots[index]?.id==c.id)startSlot(index,c,repo)}
        guide="Loading guide…";scope.launch{val text=runCatching{repo.guide(c)}.getOrDefault("Guide unavailable");if(current?.id==c.id)guide=text}
    }
    override fun onStop(){backgrounded=true;requests.suspendAll();val jobs=engines.values.map{it.close()};trackClose(scope.launch{jobs.joinAll()});engines.clear();super.onStop()}
    override fun onStart(){super.onStart();guideState.start.longValue=System.currentTimeMillis()/1800000*1800000;if(backgrounded){backgrounded=false;val epoch=requests.epoch;scope.launch{closing?.join();if(backgrounded || epoch!=requests.epoch)return@launch;ensureStreams()}}}
    override fun onDestroy(){scope.cancel();super.onDestroy()}
    @Composable private fun Video(index:Int,modifier:Modifier=Modifier){
        AndroidView(factory={PlayerView(it).apply{
            useController=false;isFocusable=false;isFocusableInTouchMode=false
            descendantFocusability=android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            resizeMode=AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }},update={bindPlaybackView(it,engines[index])},onRelease={bindPlaybackView(it,null)},modifier=modifier)
    }
    @Composable private fun MultiView(){
        var settings by remember{mutableStateOf(false)}
        val focusers=remember{List(6){FocusRequester()}}
        val focusTarget=remember(settings,layout){selectedSlot.coerceIn(0,layout-1)}
        LaunchedEffect(settings,layout){if(!settings)focusers[focusTarget].requestFocus()}
        BackHandler{settings=true}
        Box(Modifier.fillMaxSize().background(Color.Black)){
            Column(Modifier.fillMaxSize()){
                val rows=if(layout==2)1 else 2;val columns=layout/rows
                repeat(rows){r->Row(Modifier.weight(1f)){
                    repeat(columns){col->val index=r*columns+col
                        key(index){
                            var focused by remember{mutableStateOf(false)}
                            Box(Modifier.weight(1f).fillMaxHeight()){
                                Video(index,Modifier.fillMaxSize())
                                Box(Modifier.fillMaxSize().focusRequester(focusers[index]).onFocusChanged{
                                    focused=it.isFocused
                                    if(it.isFocused && !settings && multiView && !fullScreen)selectSlot(index)
                                }.clickable(onClickLabel=if(slots[index]==null)"Choose channel" else "Open fullscreen"){
                                    selectSlot(index)
                                    if(slots[index]==null){multiView=false}else{ensureStreams();audio();fullScreen=true;multiView=false}
                                }.border(if(focused)2.dp else 0.dp,if(focused)Color.White else Color.Transparent).semantics{
                                    contentDescription=slots[index]?.let{"${it.name}, select for fullscreen. Back opens wall controls."}?:"Choose channel for panel ${index+1}"
                                }){
                                    if(slots[index]==null)Text("+ Choose channel",Modifier.align(Alignment.Center))
                                }
                            }
                        }
                    }
                }}
            }
        }
        if(settings)AlertDialog(onDismissRequest={settings=false},title={Text("Video wall")},text={Column{
            TextButton(onClick={settings=false;healthOpen=true}){Text("Playback health")}
            Text("Focus a picture to hear it. Select for fullscreen. Back returns to the wall.")
            Row{listOf(2,4,6).forEach{n->TextButton(onClick={layout=n;if(selectedSlot>=n)selectSlot(0);saveScene();settings=false}){Text(if(layout==n)"✓ $n panels" else "$n panels")}}}
            if(slots.drop(layout).any{it!=null})Text("Hidden panels are retained and muted.")
            LazyColumn(Modifier.heightIn(max=260.dp)){
                items((0 until layout).toList()){index->Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text(slots[index]?.name?:"Panel ${index+1}",maxLines=1);Text(slotStatus[index].orEmpty(),style=MaterialTheme.typography.bodySmall,maxLines=1)}
                    TextButton(onClick={selectSlot(index);settings=false;multiView=false}){Text("Change")}
                    TextButton(onClick={removeSlot(index)}){Text("Remove")}
                }}
            }
        }},confirmButton={Button(onClick={settings=false}){Text("Return to wall")}},dismissButton={Row{
            TextButton(onClick={settings=false;multiView=false}){Text("Guide")}
            TextButton(onClick={settings=false;stopPlayback()}){Text("Exit wall & stop all")}
        }})
    }
    @Composable private fun App(){
        if(healthOpen)PlaybackHealthDialog(engines.map{(i,e)->(slots[i]?.name?:"Panel ${i+1}") to e},catalogMetrics){healthOpen=false}
        val c=current
        BackHandler(enabled=fullScreen || (!multiView && multiEnabled)){
            if(fullScreen){fullScreen=false;multiView=multiEnabled;audio()} else if(multiEnabled){multiView=true}
        }
        if(c!=null && fullScreen){
            var overlay by remember(c.id){mutableStateOf(true)}
            LaunchedEffect(c.id){delay(3500);overlay=false}
            Box(Modifier.fillMaxSize()){
                AndroidView(factory={PlayerView(it).apply{
                    bindPlaybackView(this,engine);keepScreenOn=true;useController=true;controllerShowTimeoutMs=3500
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener{visibility->overlay=visibility==android.view.View.VISIBLE})
                    requestFocus()
                }},update={bindPlaybackView(it,engine)},onRelease={bindPlaybackView(it,null)},modifier=Modifier.fillMaxSize())
                if(overlay)Column(Modifier.fillMaxWidth().background(Color(0xBB101216)).padding(16.dp)){
                    Text(c.name,style=MaterialTheme.typography.titleMedium);Text(status,style=MaterialTheme.typography.bodySmall);Text(guide.lines().firstOrNull().orEmpty(),style=MaterialTheme.typography.bodySmall)
                }
            };return
        }
        if(multiView){MultiView();return}
        var settings by remember{mutableStateOf(false)}
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF243A56),Color(0xFF111E30)))).padding(horizontal=24.dp,vertical=12.dp)){
            if(profile==null)Text("SIGNAL TV · $status",style=MaterialTheme.typography.titleLarge)
            if(unlockError){Text(status);Button(onClick={restoreConnection()}){Text("Retry")};Button(onClick={secure.clear();unlockError=false;status="Choose how to connect"}){Text("Reset saved login")};return@Column}
            if(profile==null){Setup();return@Column}
            if(busy && channels.isEmpty())LinearProgressIndicator(Modifier.fillMaxWidth())
            repository?.let { repo -> GuideScreen(channels,repo,guideState,onWall={wallMode=wallMode.enter();multiView=true;saveScene();ensureStreams()},onSettings={settings=true},onRefresh={profile?.let{connect(it)}},preview={height->
                Row(Modifier.fillMaxWidth().padding(bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)){
                    Column{
                        Box(Modifier.height(height).aspectRatio(16f/9f).background(Color.Black)){
                            if(c!=null)AndroidView(factory={PlayerView(it).apply{bindPlaybackView(this,engine);keepScreenOn=true;useController=false}},update={bindPlaybackView(it,engine)},onRelease={bindPlaybackView(it,null)},modifier=Modifier.fillMaxSize())
                            if(c==null)Text("Select a channel",Modifier.align(Alignment.Center))
                            if(c!=null)Box(Modifier.matchParentSize().clickable(onClickLabel="Open fullscreen"){ensureStreams();fullScreen=true})
                        }
                        if(c!=null)Row(Modifier.width(height*16f/9f).padding(top=4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            FilledTonalButton(onClick={ensureStreams();fullScreen=true}){Text("Fullscreen")}
                            TextButton(onClick={stopPlayback()}){Text("Remove all streams")}
                        }
                    }
                    Column(Modifier.weight(1f).padding(top=8.dp)){
                        val programme=guideState.focusedProgramme.value?:guideState.cache[guideState.focusedChannel.value?.id?:c?.id]?.second?.firstOrNull{it.start<=System.currentTimeMillis() && it.end>System.currentTimeMillis()}
                        Text(programme?.title?:c?.name?:"Live TV",style=MaterialTheme.typography.titleLarge,maxLines=2)
                        if(programme!=null){
                            val formatter=java.time.format.DateTimeFormatter.ofPattern("h:mm a").withZone(java.time.ZoneId.systemDefault())
                            Text(formatter.format(java.time.Instant.ofEpochMilli(programme.start))+" – "+formatter.format(java.time.Instant.ofEpochMilli(programme.end)),style=MaterialTheme.typography.bodyMedium)
                        }
                        if(!programme?.description.isNullOrBlank())Text(programme!!.description,style=MaterialTheme.typography.bodyMedium,maxLines=3)
                        Spacer(Modifier.height(8.dp))
                        Text(if(c==null)"Choose a group, then select a channel to watch." else c.name+" · "+status,style=MaterialTheme.typography.bodyMedium,maxLines=3)
                    }
                }
            },play=::play) }


        }
        if(settings)profile?.let{p->
            var http by remember{mutableStateOf(p.allowHttp)}
            var format by remember{mutableStateOf(p.format)}
            var output by remember{mutableStateOf(p.streamProfile)}
            AlertDialog(onDismissRequest={settings=false},title={Text("Connection settings")},text={Column{
                TextButton(onClick={settings=false;healthOpen=true}){Text("Playback health & performance")}
                Text(if(p.mode=="server")"Companion · Android controls the channels; your server supplies the stream." else "Standalone · streams play directly from your Xtream provider.")
                if(p.mode=="direct"){
                    Row{Checkbox(http,{http=it});Text("Allow HTTP streams and redirects (unencrypted)")}
                    Row{RadioButton(format=="m3u8",{format="m3u8"});Text("HLS",Modifier.padding(top=14.dp));RadioButton(format=="ts",{format="ts"});Text("MPEG-TS",Modifier.padding(top=14.dp))}
                }else{
                    Row{RadioButton(output=="remux",{output="remux"});Text("Original quality · server remux",Modifier.padding(top=14.dp))}
                    Row{RadioButton(output=="browser",{output="browser"});Text("Compatibility · server converts to H.264 / AAC",Modifier.padding(top=14.dp))}
                    Text("Conversion uses server CPU. Provider HTTP settings are managed on the server.",style=MaterialTheme.typography.bodySmall)
                }
            }},confirmButton={Button(onClick={settings=false;stopPlayback(false);connect(p.copy(allowHttp=http,format=format,streamProfile=output))}){Text("Save")}},dismissButton={Row{
                TextButton(onClick={settings=false;stopPlayback();connectJob?.cancel();secure.clear();profile=null;repository=null;sceneLoaded=false;channels=emptyList();guideState.cache.clear();guideState.navigationOpen.value=true;guideState.focusedProgramme.value=null;guideState.focusedChannel.value=null;guideState.groups.value=emptyList();guideState.preferences.value=GuidePreferences();guideState.selected.value="all";guideState.query.value="";status="Choose how to connect"}){Text("Change login")}
                TextButton(onClick={settings=false}){Text("Cancel")}
            }})
        }

    }
    @Composable private fun Setup(){
        var mode by remember{mutableStateOf("server")};var base by remember{mutableStateOf("")};var user by remember{mutableStateOf("")};var password by remember{mutableStateOf("")};var http by remember{mutableStateOf(false)};var format by remember{mutableStateOf("m3u8")};var pairing by remember{mutableStateOf("")}
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Button(onClick={mode="server"},enabled=!busy){Text("Companion · my server")};Button(onClick={mode="direct"},enabled=!busy){Text("Standalone · Xtream")}}
            Text(if(mode=="server")"Your server manages providers, guide data and stream recovery." else "Connect directly to your provider. Credentials are encrypted on this device.",style=MaterialTheme.typography.bodyMedium)
            TvTextInput(value=base,onValueChange={base=it},label={Text(if(mode=="server")"Server address, e.g. http://192.168.1.10:8580" else "Provider server URL")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Row{Checkbox(checked=http,onCheckedChange={http=it});Text("Allow HTTP on this connection (unencrypted)",modifier=Modifier.padding(top=14.dp))}
            if(mode=="direct"){
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){TvTextInput(value=user,onValueChange={user=it},label={Text("Username")},singleLine=true,modifier=Modifier.weight(1f));TvTextInput(value=password,onValueChange={password=it},label={Text("Password")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.weight(1f))}
                Row{RadioButton(selected=format=="m3u8",onClick={format="m3u8"});Text("HLS",Modifier.padding(top=14.dp));RadioButton(selected=format=="ts",onClick={format="ts"});Text("MPEG-TS",Modifier.padding(top=14.dp))}
                Button(enabled=!busy,onClick={try{connect(Profile("direct",validateBase(base,http),username=user,password=password,format=format,allowHttp=http))}catch(e:Exception){status=e.message?:"Invalid address"}}){Text("Sign in & load channels")}
            }else{
                if(pairing.isNotEmpty())Text("Approve code $pairing in the server dashboard",style=MaterialTheme.typography.headlineMedium,color=MaterialTheme.colorScheme.primary)
                Button(enabled=!busy,onClick={connectJob?.cancel();connectJob=scope.launch{busy=true;try{
                    val address=validateBase(base,http);val pair=JSONObject(request(address+"/api/v1/pairing/requests",method="POST",body=JSONObject().put("name","Android TV")));pairing=pair.getString("code");status="Waiting for server approval"
                    repeat(100){delay(3000);val result=JSONObject(request(address+"/api/v1/pairing/poll",method="POST",body=JSONObject().put("id",pair.getString("id")).put("deviceSecret",pair.getString("deviceSecret"))));if(result.optString("state")=="approved"){val p=Profile("server",address,token=result.getString("token"),allowHttp=http);pairing="";connectJob=null;connect(p);return@launch}}
                    status="Pairing expired. Try again."
                }catch(e:CancellationException){throw e}catch(e:Exception){status=e.message?:"Pairing failed"}finally{busy=false}}}){Text("Pair this device")}
            }
            Text("Signal TV 0.5.0 · Live guide, custom groups and multiview.",style=MaterialTheme.typography.bodySmall)
        }
    }
}
