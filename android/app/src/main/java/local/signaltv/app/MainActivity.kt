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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
    private var tmdbOpen by mutableStateOf(false)
    private var metadataRevision by mutableIntStateOf(0)
    private var profile by mutableStateOf<Profile?>(null)
    private var channels by mutableStateOf<List<Channel>>(emptyList())
    private var status by mutableStateOf("Choose how to connect")
    private var current by mutableStateOf<Channel?>(null)
    private var fullScreen by mutableStateOf(false)
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
    private var guideState by mutableStateOf(GuideUiState())
    private var managingAccounts by mutableStateOf(false)
    private var backgrounded=false
    private var sceneLoaded=false
    private var closing: Job?=null
    private val requests=SlotRequests()
    private var repository by mutableStateOf<Repository?>(null)
    private var connectJob: Job?=null
    private var connectionRetry:Job?=null
    private var busy by mutableStateOf(false)
    private var healthOpen by mutableStateOf(false)
    private var qualityOpen by mutableStateOf(false)
    private var catalogMetrics by mutableStateOf("Catalog measurements pending")
    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState);secure=SecureProfile(this)
        restoreConnection()
        RefreshJobs.schedule(this)
        RecordingStore.schedule(this)
        scope.launch{withContext(Dispatchers.IO){runCatching{RefreshJobs.run(this@MainActivity)}}}
        Appearance.load(this)
        setContent{AppearanceTheme{Surface(Modifier.fillMaxSize()){App()}}}
    }
    private fun openRecordings(schedule:Boolean=false){
        val p=profile?:return
        val channel=guideState.focusedChannel.value?:current
        val programme=guideState.focusedProgramme.value?:currentProgramme(guideState.cache[channel?.id]?.second.orEmpty(),System.currentTimeMillis())
        startActivity(android.content.Intent(this,RecordingActivity::class.java).putExtra("account",p.accountKey()).putExtra("schedule",schedule).putExtra("channel",channel?.id).putExtra("title",programme?.title?:channel?.name).putExtra("start",programme?.start?:System.currentTimeMillis()).putExtra("end",programme?.end?:System.currentTimeMillis()+3600000))
    }
    private fun restoreConnection(){try{unlockError=false;secure.load()?.let{profile=it;connect(it)}}catch(e:Exception){unlockError=true;status=e.message?:"Unable to unlock connection"}}
    private fun connect(p: Profile,replacing:Profile?=null){
        connectJob?.cancel();connectionRetry?.cancel()
        val savedConnection=replacing==null && secure.accounts().any{it==p}
        val previousProfile=profile;val previousRepository=repository;val previousChannels=channels;val previousGuide=guideState
        if(profile?.accountKey()!=p.accountKey()){stopPlayback(false);channels=emptyList();guideState=GuideUiState();repository=null;sceneLoaded=false}
        val repo=repository?.takeIf{it.profile==p}?:if(p.mode=="server")ServerRepository(p)else XtreamRepository(p,this)
        repository=repo
        connectJob=scope.launch{val started=android.os.SystemClock.elapsedRealtime();busy=true;try{status="Loading channels…";if(channels.isEmpty())channels=withContext(Dispatchers.IO){CatalogCache(this@MainActivity,p).load()};catalogMetrics="Cached catalog: ${android.os.SystemClock.elapsedRealtime()-started}ms · ${channels.size} channels";val result=withContext(Dispatchers.IO){repo.channels().also{require(it.isNotEmpty()){"No channels returned; saved lineup retained"};CatalogCache(this@MainActivity,p).save(it)}};secure.save(p,replacing);profile=p;managingAccounts=false;RefreshJobs.schedule(this@MainActivity);channels=result;catalogMetrics+=" · refreshed: ${android.os.SystemClock.elapsedRealtime()-started}ms";restoreScene();status="${result.size} channels · ${if(p.mode=="server")"Companion" else "Standalone"}"}catch(e:CancellationException){throw e}catch(e:Exception){if(savedConnection){profile=p;secure.save(p);managingAccounts=false;restoreScene();retrySavedConnection(repo)}else if(previousRepository!=null || previousProfile?.accountKey()!=p.accountKey()){profile=previousProfile;repository=previousRepository;channels=previousChannels;guideState=previousGuide};status=(e.message?:"Connection failed")+if(savedConnection)" · retrying automatically" else "";guideState.refreshMessage.value=status}finally{busy=false}}
    }
    private fun retrySavedConnection(repo:Repository){connectionRetry?.cancel();connectionRetry=scope.launch{
        var wait=30000L
        while(isActive && repository===repo && profile==repo.profile){delay(wait);try{
            val result=repo.channels();require(result.isNotEmpty())
            withContext(Dispatchers.IO){CatalogCache(this@MainActivity,repo.profile).save(result)}
            if(repository===repo && profile==repo.profile){channels=result;guideState.refreshMessage.value="Provider reconnected";status="${result.size} channels"};return@launch
        }catch(e:CancellationException){throw e}catch(_:Exception){wait=(wait*2).coerceAtMost(300000L)}}
    }}
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
        val playbackRepo=if(repo.profile.mode=="server")ServerRepository(repo.profile.copy(streamProfile=companionQuality(PlaybackQuality(this,repo.profile).get())))else repo
        engines[index]=PlaybackEngine(this,playbackRepo){slotStatus[index]=it;if(index==selectedSlot)status=it}.also{it.setAudible(false);it.start(c)}
        audio()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    private fun selectSlot(index:Int){selectedSlot=index;current=slots[index];status=slotStatus[index].orEmpty();audio();saveScene()}
    private fun play(c: Channel){
        val repo=repository?:return
        GuideExperience(this,repo.profile).watched(c.id)
        val existing=slots.indexOfFirst{it?.id==c.id}
        if(existing>=0){selectSlot(existing);ensureStreams();fullScreen=true;multiView=false;return}
        if(slots[selectedSlot]==null && slots.count{it!=null}>=repo.connectionLimit){status="Provider allows ${repo.connectionLimit} simultaneous connections. Remove a panel first.";return}
        val index=selectedSlot;val previous=engines.remove(index)?.close();previous?.let{trackClose(it)};slots[index]=c;current=c;fullScreen=!multiEnabled;multiView=multiEnabled
        saveScene();val ticket=requests.begin(index)
        scope.launch{previous?.join();closing?.join();if(requests.accepts(ticket) && slots[index]?.id==c.id)startSlot(index,c,repo)}
    }
    override fun onStop(){backgrounded=true;requests.suspendAll();val jobs=engines.values.map{it.close()};trackClose(scope.launch{jobs.joinAll()});engines.clear();super.onStop()}
    override fun onStart(){super.onStart();guideState.start.longValue=System.currentTimeMillis();if(backgrounded){backgrounded=false;val epoch=requests.epoch;scope.launch{closing?.join();if(backgrounded || epoch!=requests.epoch)return@launch;ensureStreams()}}}
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
                                    if(slots[index]==null)Text("+ Choose channel",Modifier.align(Alignment.Center),color=Color.White)
                                }
                            }
                        }
                    }
                }}
            }
        }
        if(settings)AlertDialog(onDismissRequest={settings=false},title={Text("Video wall")},text={Column{
            TextButton(onClick={settings=false;qualityOpen=true}){Text("Playback quality")}
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
        LaunchedEffect(profile){profile?.let{guideState.home.value=Appearance.current.startup=="home";if(Appearance.current.startup=="wall"){wallMode=wallMode.enter();multiView=true;saveScene()};Appearance.connect(it)}}
        LaunchedEffect(profile){profile?.let{TmdbMetadata.get(this@MainActivity).warm(applicationContext,it)}}
        if(tmdbOpen)TmdbSettings(profile){tmdbOpen=false;metadataRevision++}
        LaunchedEffect(profile){val p=profile?:return@LaunchedEffect;val cache=CatalogCache(this@MainActivity,p);var stamp=cache.modified();while(true){delay(60000);if(stamp!=cache.modified()){val entries=withContext(Dispatchers.IO){cache.load()};if(profile==p&&entries.isNotEmpty())channels=entries;stamp=cache.modified()}}}

        if(managingAccounts){Surface(Modifier.fillMaxSize()){Column(Modifier.padding(24.dp)){Setup()}};return}
        if(qualityOpen)profile?.let{PlaybackQualityDialog(it){qualityOpen=false}}
        if(healthOpen)PlaybackHealthDialog(engines.map{(i,e)->(slots[i]?.name?:"Panel ${i+1}") to e},catalogMetrics){healthOpen=false}
        val c=current
        BackHandler(enabled=fullScreen || (!multiView && multiEnabled)){
            if(fullScreen){if(!multiEnabled)current?.let{guideState.returnChannel.value=it.id;guideState.navigationOpen.value=false};fullScreen=false;multiView=multiEnabled;audio()} else if(multiEnabled){multiView=true}
        }
        if(c!=null && fullScreen){
            var liveProgrammes by remember(c.id){mutableStateOf(guideState.cache[c.id]?.second.orEmpty())}
            var liveNow by remember(c.id){mutableLongStateOf(System.currentTimeMillis())}
            LaunchedEffect(c.id,repository){
                while(true){
                    liveNow=System.currentTimeMillis()
                    try{liveProgrammes=repository?.programmes(c).orEmpty()}
                    catch(e:CancellationException){throw e}
                    catch(_:Exception){} // Retain this channel's known listings on transient failure.
                    liveNow=System.currentTimeMillis()
                    delay(30000)
                }
            }
            FullscreenOverlay(c,liveProgrammes,liveNow,
                channels=channels, recent=profile?.let{GuideExperience(this,it).recent()}.orEmpty(),
                cached=guideState.cache.mapValues{it.value.second},
                onGuide={guideState.returnChannel.value=c.id;guideState.navigationOpen.value=false;fullScreen=false;multiView=multiEnabled;audio()},
                onTune={play(it);fullScreen=true;multiView=false},
                video={AndroidView(factory={PlayerView(it).apply{bindPlaybackView(this,engine);keepScreenOn=true;useController=false}},update={bindPlaybackView(it,engine)},onRelease={bindPlaybackView(it,null)},modifier=Modifier.fillMaxSize())})
            return
        }
        if(multiView){MultiView();return}
        var settings by remember{mutableStateOf(false)}
        var appearanceOpen by remember{mutableStateOf(false)}
        if(appearanceOpen)AppearanceStudio(profile){appearanceOpen=false}
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(TvPalette.Glow,TvPalette.Background))).padding(horizontal=24.dp,vertical=12.dp)){
            if(profile==null)Text("VYNODE IPTV · $status",style=MaterialTheme.typography.titleLarge)
            if(unlockError){Text(status);Button(onClick={restoreConnection()}){Text("Retry")};Button(onClick={secure.clear();unlockError=false;status="Choose how to connect"}){Text("Reset saved login")};return@Column}
            if(profile==null){Setup();return@Column}
            if(busy && channels.isEmpty())LinearProgressIndicator(Modifier.fillMaxWidth())
            repository?.let { repo ->
             if(guideState.home.value)DiscoverScreen(channels,repo,guideState,guide={guideState.home.value=false},wall={guideState.home.value=false;wallMode=wallMode.enter();multiView=true;saveScene();ensureStreams()},settings={appearanceOpen=true},play={guideState.home.value=false;play(it)})
             else GuideScreen(channels,repo,guideState,onHome={guideState.home.value=true},onWall={wallMode=wallMode.enter();multiView=true;saveScene();ensureStreams()},onSettings={settings=true},onRefresh={refreshChannels()},onRefreshEpg={refreshEpg()},preview={height->
                PreviewLayout(Appearance.current.layout=="side",Modifier.fillMaxWidth().padding(bottom=8.dp)){
                    Column{
                        Box(Modifier.height(height).aspectRatio(16f/9f).clip(RoundedCornerShape(Appearance.current.radius.dp)).background(Color.Black).border(1.dp,TvPalette.Line,RoundedCornerShape(Appearance.current.radius.dp))){
                            if(c!=null)AndroidView(factory={PlayerView(it).apply{bindPlaybackView(this,engine);keepScreenOn=true;useController=false}},update={bindPlaybackView(it,engine)},onRelease={bindPlaybackView(it,null)},modifier=Modifier.fillMaxSize())
                            if(c==null&&height>=90.dp)Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally){Text("VYNODE",style=MaterialTheme.typography.headlineMedium,color=Color.White);Text("Choose something worth watching",style=MaterialTheme.typography.bodySmall,color=Color.LightGray)}
                            if(c!=null)Box(Modifier.matchParentSize().clickable(onClickLabel="Open fullscreen"){ensureStreams();fullScreen=true})
                        }
                    }
                    val featuredChannel=guideState.focusedChannel.value?:c
                    val programme=guideState.focusedProgramme.value?:currentProgramme(guideState.cache[featuredChannel?.id]?.second.orEmpty(),System.currentTimeMillis())
                    Column{
                        var details by remember{mutableStateOf(false)}
                        if(details)AlertDialog(onDismissRequest={details=false},text={Column(Modifier.heightIn(max=340.dp).verticalScroll(rememberScrollState())){ProgrammeFeature(featuredChannel,programme,c,Modifier.fillMaxWidth(),profile)}},confirmButton={TextButton(onClick={details=false}){Text("Close")}})
                        Row{
                            if(Appearance.current.layout!="wall")key(metadataRevision){ProgrammeFeature(featuredChannel,programme,c,Modifier.weight(1f).height(height),profile,compact=true)}else Spacer(Modifier.weight(1f))
                            Box{
                                var menu by remember{mutableStateOf(false)}
                                IconButton(onClick={menu=true}){Text("⋮",style=MaterialTheme.typography.headlineSmall)}
                                DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                                    DropdownMenuItem(text={Text("Programme details")},onClick={menu=false;details=true})
                                    DropdownMenuItem(text={Text("Fullscreen")},enabled=c!=null,onClick={menu=false;ensureStreams();fullScreen=true})
                                    DropdownMenuItem(text={Text("Stop all streams")},enabled=c!=null,onClick={menu=false;stopPlayback()})
                                    DropdownMenuItem(text={Text("Record / schedule")},onClick={menu=false;openRecordings(true)})
                                    DropdownMenuItem(text={Text("Recordings")},onClick={menu=false;openRecordings()})
                                    DropdownMenuItem(text={Text("Video wall")},onClick={menu=false;wallMode=wallMode.enter();multiView=true;saveScene();ensureStreams()})
                                    DropdownMenuItem(text={Text("Settings")},onClick={menu=false;settings=true})
                                }
                            }
                        }
                    }
                }
            },play=::play) }


        }
        if(settings)profile?.let{p->
            var http by remember{mutableStateOf(p.allowHttp)}
            var format by remember{mutableStateOf(p.format)}
            var fallback by remember{mutableStateOf(p.httpFallback)}
            var output by remember{mutableStateOf(p.streamProfile)}
            val refreshPrefs=remember(p){RefreshPreferences(this,p)}
            var dailyChannels by remember{mutableStateOf(refreshPrefs.channels)}
            var dailyEpg by remember{mutableStateOf(refreshPrefs.epg)}
            AlertDialog(onDismissRequest={settings=false},title={Text("Connection settings")},text={Column(Modifier.heightIn(max=300.dp).verticalScroll(rememberScrollState())){
                TextButton(onClick={settings=false;qualityOpen=true}){Text("Playback quality")}
            TextButton(onClick={settings=false;healthOpen=true}){Text("Playback health & performance")}
                TextButton(onClick={settings=false;appearanceOpen=true}){Text("Appearance Studio")}
                TextButton(onClick={settings=false;tmdbOpen=true}){Text("Metadata · TMDB")}
                Row{Checkbox(dailyChannels,{dailyChannels=it});Text("Refresh channels daily",Modifier.padding(top=14.dp))}
                Row{Checkbox(dailyEpg,{dailyEpg=it});Text("Refresh EPG daily",Modifier.padding(top=14.dp))}
                Text("Runs when Android allows background work and a network is available.",style=MaterialTheme.typography.bodySmall)
                Text(if(p.mode=="server")"Companion · Android controls the channels; your server supplies the stream." else "Standalone · streams play directly from your Xtream provider.")
                if(p.mode=="direct"){
                    Row{Checkbox(http,{http=it});Text("Allow HTTP streams and redirects (unencrypted)")}
                    Row{Checkbox(fallback,{fallback=it},enabled=http);Text("Automatic HTTP fallback (credentials sent unencrypted)")}
                    Row{RadioButton(format=="m3u8",{format="m3u8"});Text("HLS",Modifier.padding(top=14.dp));RadioButton(format=="ts",{format="ts"});Text("MPEG-TS",Modifier.padding(top=14.dp))}
                }else{
                    Row{RadioButton(output=="remux",{output="remux"});Text("Original quality · server remux",Modifier.padding(top=14.dp))}
                    Row{RadioButton(output=="browser",{output="browser"});Text("Compatibility · server converts to H.264 / AAC",Modifier.padding(top=14.dp))}
                    Text("Conversion uses server CPU. Provider HTTP settings are managed on the server.",style=MaterialTheme.typography.bodySmall)
                }
            }},confirmButton={Button(onClick={settings=false;refreshPrefs.set(dailyChannels,dailyEpg);RefreshJobs.schedule(this);val updated=p.copy(allowHttp=http,format=format,streamProfile=output,httpFallback=fallback&&http);if(updated!=p){stopPlayback(false);connect(updated)}}){Text("Save")}},dismissButton={Row{
                TextButton(onClick={settings=false;stopPlayback(false);connectJob?.cancel();managingAccounts=true}){Text("Switch mode / accounts")}
                TextButton(onClick={settings=false;stopPlayback(false);connectJob?.cancel();secure.logout();profile=null;repository=null;channels=emptyList();guideState=GuideUiState();managingAccounts=true}){Text("Log out")}

                TextButton(onClick={settings=false}){Text("Cancel")}
            }})
        }

    }
    private var refreshJob:Job?=null
    private fun refreshChannels(){val repo=repository?:return;val state=guideState;if(refreshJob?.isActive==true)return;refreshJob=scope.launch{state.refreshMessage.value="Refreshing channels…";try{val result=repo.refreshChannels();withContext(Dispatchers.IO){CatalogCache(this@MainActivity,repo.profile).save(result)};if(repository===repo){channels=result;RefreshPreferences(this@MainActivity,repo.profile).success("channels");state.refreshMessage.value="Channels refreshed"}}catch(e:CancellationException){throw e}catch(e:Exception){state.refreshMessage.value=e.message?:"Channel refresh failed"}}}
    private fun refreshEpg(){val repo=repository?:return;val state=guideState;if(refreshJob?.isActive==true)return;refreshJob=scope.launch{state.refreshMessage.value="Refreshing EPG…";try{repo.refreshEpg(channels);if(repository===repo){state.cache.clear();state.cacheWindows.clear();state.refreshRevision.intValue++;state.focusedProgramme.value=null;RefreshPreferences(this@MainActivity,repo.profile).success("epg");state.refreshMessage.value="EPG refreshed"}}catch(e:CancellationException){throw e}catch(e:Exception){state.refreshMessage.value=e.message?:"EPG refresh failed"}}}
    @Composable private fun Setup(){
        var saved by remember{mutableStateOf(secure.accounts())}
        var form by remember{mutableStateOf(saved.isEmpty())}
        var edit by remember{mutableStateOf<Profile?>(null)}
        var remove by remember{mutableStateOf<Profile?>(null)}
        if(!form){
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("Accounts & playback mode",style=MaterialTheme.typography.headlineMedium)
                Text("Standalone works directly with your provider. Companion uses your saved server. Switch here at any time; each connection keeps its own favorites and groups.")
                saved.forEach{p->Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(p.mode=="direct")"Standalone · ${p.username}" else "Companion · server");Text(p.base,style=MaterialTheme.typography.bodySmall)};TextButton(enabled=!busy,onClick={connect(p)}){Text(if(profile?.accountKey()==p.accountKey())"Reconnect" else "Use this")};TextButton(enabled=!busy,onClick={edit=p;form=true}){Text("Edit")};TextButton(enabled=!busy,onClick={remove=p}){Text("Delete")}}}
                Button(enabled=!busy,onClick={edit=null;form=true}){Text("Add account")}
                TextButton(onClick={tmdbOpen=true}){Text("Metadata · TMDB")}
                if(profile!=null)TextButton(onClick={managingAccounts=false}){Text("Back to guide")}
                Text(status)
            }
            remove?.let{p->AlertDialog(onDismissRequest={remove=null},title={Text("Delete saved login?")},text={Text("Credentials for ${p.username.ifBlank{p.base}} will be removed. Favorites and groups stay saved for this account.")},confirmButton={TextButton(onClick={secure.remove(p);if(profile?.accountKey()==p.accountKey()){profile=null;repository=null;channels=emptyList();guideState=GuideUiState()};saved=secure.accounts();remove=null}){Text("Delete login")}},dismissButton={TextButton(onClick={remove=null}){Text("Cancel")}})}
            return
        }

        var mode by remember(edit){mutableStateOf(edit?.mode?:"direct")};var base by remember(edit){mutableStateOf(edit?.base.orEmpty())};var user by remember(edit){mutableStateOf(edit?.username.orEmpty())};var password by remember(edit){mutableStateOf(edit?.password.orEmpty())};var http by remember(edit){mutableStateOf(edit?.allowHttp?:false)};var format by remember(edit){mutableStateOf(edit?.format?:"m3u8")};var pairing by remember{mutableStateOf("")};var fallback by remember(edit){mutableStateOf(edit?.httpFallback?:false)}
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            TextButton(enabled=!busy,onClick={form=false;saved=secure.accounts()}){Text("‹ Saved accounts")}
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Button(onClick={mode="server"},enabled=!busy){Text("Companion · my server")};Button(onClick={mode="direct"},enabled=!busy){Text("Standalone · Xtream")}}
            Text(if(mode=="server")"Your server manages providers, guide data and stream recovery." else "Connect directly to your provider. Credentials are encrypted on this device.",style=MaterialTheme.typography.bodyMedium)
            TvTextInput(value=base,onValueChange={base=it},label={Text(if(mode=="server")"Server address, e.g. http://192.168.1.10:8580" else "Provider server URL")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Row{Checkbox(checked=http,onCheckedChange={http=it});Text("Allow HTTP on this connection (unencrypted)",modifier=Modifier.padding(top=14.dp))}
            if(mode=="direct"){
                Row{Checkbox(fallback,{fallback=it},enabled=http);Text("Automatic HTTP fallback (credentials sent unencrypted)")}
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){TvTextInput(value=user,onValueChange={user=it},label={Text("Username")},singleLine=true,modifier=Modifier.weight(1f));TvTextInput(value=password,onValueChange={password=it},label={Text("Password")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.weight(1f))}
                Row{RadioButton(selected=format=="m3u8",onClick={format="m3u8"});Text("HLS",Modifier.padding(top=14.dp));RadioButton(selected=format=="ts",onClick={format="ts"});Text("MPEG-TS",Modifier.padding(top=14.dp))}
                Button(enabled=!busy,onClick={try{connect(Profile("direct",validateBase(base,http),username=user,password=password,format=format,allowHttp=http,httpFallback=fallback&&http),edit)}catch(e:Exception){status=e.message?:"Invalid address"}}){Text("Sign in & load channels")}
            }else{
                val existing=edit
                if(existing?.mode=="server")Button(enabled=!busy,onClick={try{val address=validateBase(base,http);require(address==existing.base){"Pair again to use a different server address"};connect(existing.copy(allowHttp=http),existing)}catch(e:Exception){status=e.message?:"Invalid address"}}){Text("Save connection")}
                if(pairing.isNotEmpty())Text("Approve code $pairing in the server dashboard",style=MaterialTheme.typography.headlineMedium,color=MaterialTheme.colorScheme.primary)
                Button(enabled=!busy,onClick={connectJob?.cancel();connectJob=scope.launch{busy=true;try{
                    val address=validateBase(base,http);val pair=JSONObject(request(address+"/api/v1/pairing/requests",method="POST",body=JSONObject().put("name","Android TV")));pairing=pair.getString("code");status="Waiting for server approval"
                    repeat(100){delay(3000);val result=JSONObject(request(address+"/api/v1/pairing/poll",method="POST",body=JSONObject().put("id",pair.getString("id")).put("deviceSecret",pair.getString("deviceSecret"))));if(result.optString("state")=="approved"){val p=Profile("server",address,token=result.getString("token"),allowHttp=http);pairing="";connectJob=null;connect(p,edit);return@launch}}
                    status="Pairing expired. Try again."
                }catch(e:CancellationException){throw e}catch(e:Exception){status=e.message?:"Pairing failed"}finally{busy=false}}}){Text("Pair this device")}
            }
            Text("VynodeIPTV · Saved accounts and personal channel collections.",style=MaterialTheme.typography.bodySmall)
            Text(status)
        }
    }
}
