package local.signaltv.app

import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun clock(value: Long)=DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))

private suspend fun requestAttachedFocus(requester:FocusRequester){
    repeat(3){
        withFrameNanos{}
        try{if(requester.requestFocus())return}catch(_:IllegalStateException){ /* Item is not attached yet. */ }
    }
}

class GuideUiState {
    val home=mutableStateOf(false)
    val refreshMessage=mutableStateOf("")
    val refreshRevision=mutableIntStateOf(0)
    val returnChannel=mutableStateOf<String?>(null)
    val navigationOpen=mutableStateOf(true)
    val focusedChannel=mutableStateOf<Channel?>(null)
    val focusedProgramme=mutableStateOf<Programme?>(null)
    val selected=mutableStateOf("all")
    val query=mutableStateOf("")
    val start=mutableLongStateOf(System.currentTimeMillis())
    val groups=mutableStateOf<List<CustomGroup>>(emptyList())
    val preferences=mutableStateOf(GuidePreferences())
    val cacheWindows=mutableStateMapOf<String,Long>()
    val cache=mutableStateMapOf<String,Pair<Long,List<Programme>>>()
    val channelScroll=LazyListState()
    val groupScroll=LazyListState()
}

@Composable fun GuideScreen(channels: List<Channel>, repo: Repository, state: GuideUiState, preview: @Composable (androidx.compose.ui.unit.Dp)->Unit = {}, onHome:()->Unit={},onWall:()->Unit={}, onSettings:()->Unit={}, onRefresh:()->Unit={}, onRefreshEpg:()->Unit={}, play: (Channel)->Unit) {
    val context=LocalContext.current
    val focusManager=LocalFocusManager.current
    val store=remember(repo.profile){Groups(context,repo.profile)}
    val scope=rememberCoroutineScope()
    var navigationOpen by state.navigationOpen
    val firstChannel=remember{FocusRequester()}
    val returnFocus=remember{FocusRequester()}
    val groupFocus=remember{FocusRequester()}
    var focusGuide by remember{mutableStateOf(false)}
    var groups by state.groups
    var preferences by state.preferences
    var selected by state.selected
    var query by state.query
    var start by state.start
    var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    var message by remember{mutableStateOf("")}
    var editing by remember{mutableStateOf<CustomGroup?>(null)}
    var arranging by remember{mutableStateOf(false)}
    var searching by remember{mutableStateOf(false)}
    var searchDraft by remember{mutableStateOf("")}
    var saving by remember{mutableStateOf(false)}
    val experience=remember(repo.profile){GuideExperience(context,repo.profile)}
    val density=Appearance.current.guideDensity
    val artwork=Appearance.current.artwork=="all"
    val rowHeight=(density.row*LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
    LaunchedEffect(repo.profile){Appearance.migrateLegacy(context,repo.profile)}
    var appearance by remember{mutableStateOf(false)}
    var globalSearch by remember{mutableStateOf(false)}
    var recentOpen by remember{mutableStateOf(false)}
    var contextTarget by remember{mutableStateOf<Pair<Channel,Programme?>?>(null)}
    var detailTarget by remember{mutableStateOf<Pair<Channel,Programme?>?>(null)}
    fun contextMenu(c:Channel,p:Programme?){contextTarget=c to p}
    fun closeContext(restore:Boolean=true){
        if(restore)contextTarget?.first?.let{state.returnChannel.value=it.id}
        contextTarget=null
    }

    val cache=state.cache
    val windowStart=start/21600000*21600000
    fun shiftTime(delta:Long){val current=System.currentTimeMillis();start=guideStartAfterShift(start,delta,current)}
    val providerGroups=remember(channels){channels.groupBy { it.groupId }}
    val custom=groups.find { "custom:"+it.id==selected }
    val filtered=remember(channels,query,selected,groups,preferences.favorites){channels.filter { (selected=="all" || (selected=="favorites" && it.id in preferences.favorites) || (custom?.channelIds?.contains(it.id) ?: ("provider:"+it.groupId==selected))) && it.name.contains(query,true) }}
    val visible by remember(filtered){derivedStateOf {
        val first=(state.channelScroll.layoutInfo.visibleItemsInfo.firstOrNull()?.index?:0).coerceAtLeast(0)
        val last=state.channelScroll.layoutInfo.visibleItemsInfo.lastOrNull()?.index?:7
        filtered.drop((first-2).coerceAtLeast(0)).take((last-first+5).coerceIn(8,24))
    }}
    val metadataService=remember{TmdbMetadata.get(context)}
    val metadata=remember(repo.profile,metadataService.settingsRevision){mutableStateMapOf<String,Pair<Long,org.json.JSONObject?>>() }
    val enrichment=visible.flatMap{cache[it.id]?.second.orEmpty()}.filter{it.end>start && it.start<start+5400000}.distinctBy{programmeMetadataKey(it)}.take(64)
    LaunchedEffect(repo.profile,enrichment,metadataService.settingsRevision,now/60000){
        delay(500)
        for(p in enrichment){
            val key=programmeMetadataKey(p)
            if((metadata[key]?.first?:0L)>System.currentTimeMillis())continue
            try{val result=metadataService.lookup(repo.profile,p);metadata[key]=(System.currentTimeMillis()+if(result==null)60000L else 7*86400000L) to result}catch(e:CancellationException){throw e}catch(_:Exception){}
            while(metadata.size>200)metadata.remove(metadata.keys.first())
        }
    }
    val groupRows=(listOf(Triple("all","All channels",channels.size))+groups.map{Triple("custom:"+it.id,it.name,it.channelIds.size)}+providerGroups.map{(id,list)->Triple("provider:"+id,list.first().groupName,list.size)}).sortedWith(compareBy<Triple<String,String,Int>>{preferences.groupOrder.indexOf(it.first).let{index->if(index<0)Int.MAX_VALUE else index}}.thenBy{it.second.lowercase()}.thenBy{it.first})
    fun savePreferences(next: GuidePreferences){
        if(saving)return
        saving=true
        scope.launch{
            try{
                store.savePreferences(next)
                // Move focus before removing a focused favorite row from the lazy list.
                val removed=selected=="favorites" && preferences.favorites.any{it !in next.favorites}
                if(removed)focusManager.clearFocus(force=true)
                preferences=next
                message=""
                if(removed){state.channelScroll.scrollToItem(0);focusGuide=true}
            }catch(e:CancellationException){throw e}
            catch(e:Exception){message=e.message?:"Could not save preferences"}
            finally{saving=false}
        }
    }
    fun choose(id:String){selected=id;navigationOpen=false;scope.launch{state.channelScroll.scrollToItem(0);focusGuide=true}}
    BackHandler(enabled=!navigationOpen && editing==null && !arranging){navigationOpen=true}
    LaunchedEffect(state.returnChannel.value,filtered){
        val id=state.returnChannel.value?:return@LaunchedEffect
        val index=filtered.indexOfFirst{it.id==id}
        if(index<0){
            if(channels.any{it.id==id}){selected="all";query=""}else state.returnChannel.value=null
            return@LaunchedEffect
        }
        state.channelScroll.scrollToItem(index)
        withFrameNanos{}
        requestAttachedFocus(returnFocus)
        state.returnChannel.value=null
    }
    LaunchedEffect(focusGuide,filtered){if(focusGuide && filtered.isNotEmpty()){withFrameNanos{};requestAttachedFocus(firstChannel);focusGuide=false}}
    LaunchedEffect(navigationOpen){if(navigationOpen){
        val index=groupRows.filter{it.first !in preferences.hiddenGroups}.indexOfFirst{it.first==selected}
        if(index>=0)state.groupScroll.scrollToItem(index)
        withFrameNanos{}
        if(selected=="favorites" || index>=0)requestAttachedFocus(groupFocus)
    }}
    LaunchedEffect(store){try{groups=store.load();preferences=store.preferences()}catch(e:Exception){message=e.message?:"Could not load groups"}}
    LaunchedEffect(Unit){while(true){delay(30000);now=System.currentTimeMillis()}}
    LaunchedEffect(state.refreshRevision.intValue,visible.map{it.id},now/300000,if(repo is ServerRepository)windowStart else 0L){
        delay(150)
        val missing=visible.filter{System.currentTimeMillis()-(cache[it.id]?.first?:0)>=300000 || (repo is ServerRepository && state.cacheWindows[it.id]!=windowStart)}
        if(repo is ServerRepository){
            try{repo.programmeBatch(missing,windowStart,windowStart+43200000).forEach{(id,entries)->cache[id]=System.currentTimeMillis() to entries;state.cacheWindows[id]=windowStart};while(cache.size>300)cache.entries.minByOrNull{it.value.first}?.key?.let{cache.remove(it);state.cacheWindows.remove(it)}}
            catch(e:CancellationException){throw e}catch(_:Exception){missing.forEach{c->cache[c.id]=System.currentTimeMillis() to cache[c.id]?.second.orEmpty();state.cacheWindows[c.id]=windowStart};message="EPG unavailable · retrying automatically"}
            return@LaunchedEffect
        }
        kotlinx.coroutines.supervisorScope {
            val permits=kotlinx.coroutines.sync.Semaphore(3)
            missing.forEach{c->launch{
                permits.acquire()
                try{cache[c.id]=System.currentTimeMillis() to repo.programmes(c);if(cache.size>300)cache.entries.minByOrNull{it.value.first}?.key?.let{cache.remove(it);state.cacheWindows.remove(it)}}
                catch(e:CancellationException){throw e}catch(_:Exception){cache[c.id]=System.currentTimeMillis() to cache[c.id]?.second.orEmpty();message="Some EPG data is unavailable. Channels can still play."}
                finally{permits.release()}
            }}
        }
    }
    Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
        if(navigationOpen)Column(Modifier.width(64.dp).fillMaxHeight().appearancePanel().padding(4.dp)){
            Text("V",Modifier.padding(14.dp),color=TvPalette.Signal,fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleLarge)
            RailIconButton(R.drawable.rail_search,"Search channels",showLabel=!searching){searchDraft=query;searching=true}
            RailIconButton(R.drawable.rail_favorite,"Favorites"){choose("favorites")}
            RailIconButton(R.drawable.rail_wall,"Video wall",action=onWall)
            RailIconButton(R.drawable.rail_sync,"Refresh channels",action=onRefresh)
            RailIconButton(R.drawable.rail_epg,"Refresh EPG",action=onRefreshEpg)
            Spacer(Modifier.weight(1f))
            RailIconButton(R.drawable.rail_settings,"Connection settings",action=onSettings)
        }
        if(navigationOpen)Column(Modifier.width(180.dp).fillMaxHeight().appearancePanel().padding(8.dp)){
            Text("YOUR COLLECTION",color=TvPalette.Muted,fontSize=10.sp,letterSpacing=1.5.sp,modifier=Modifier.padding(8.dp))
            GuideButton("★ Favorites · ${channels.count{it.id in preferences.favorites}}",selected=="favorites",modifier=if(selected=="favorites")Modifier.focusRequester(groupFocus)else Modifier){choose("favorites")}
            Row{TextButton(onClick={editing=CustomGroup("","",emptySet())}){Text("+ New")};TextButton(onClick={arranging=true}){Text("Manage")}}
            LazyColumn(state=state.groupScroll,verticalArrangement=Arrangement.spacedBy(3.dp)){
                items(groupRows.filter{it.first !in preferences.hiddenGroups},key={it.first}){g->GuideButton(g.second+" · "+g.third,selected==g.first,modifier=if(selected==g.first)Modifier.focusRequester(groupFocus)else Modifier){choose(g.first)}}
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()){
          val previewHeight=when(Appearance.current.layout){"guide"->64.dp;"wall"->(maxHeight*.55f).coerceIn(180.dp,270.dp);"cinema"->(maxHeight*.40f).coerceIn(156.dp,210.dp);else->(maxHeight*.29f).coerceIn(120.dp,156.dp)}
          val side=Appearance.current.layout=="side"
          val sideWidth=(maxWidth*.32f).coerceIn(220.dp,300.dp)
          Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
          if(side)Box(Modifier.width(sideWidth)){preview(120.dp)}
          Column(Modifier.weight(1f).fillMaxHeight()){
            if(!side)preview(previewHeight)
            Row(Modifier.fillMaxWidth().height(36.dp),verticalAlignment=Alignment.CenterVertically){
                Row(Modifier.width(170.dp),verticalAlignment=Alignment.CenterVertically){
                    Box{
                        var menu by remember{mutableStateOf(false)}
                        IconButton(onClick={menu=true},modifier=Modifier.width(36.dp).semantics{contentDescription="Guide menu"}){Text("☰")}
                        DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                            DropdownMenuItem(text={Text("Discover home")},onClick={menu=false;onHome()})
                            DropdownMenuItem(text={Text("Search everything")},onClick={menu=false;globalSearch=true})
                            DropdownMenuItem(text={Text("Recent channels")},onClick={menu=false;recentOpen=true})
                            DropdownMenuItem(text={Text("Guide appearance")},onClick={menu=false;appearance=true})
                            DropdownMenuItem(text={Text("Channel groups")},onClick={menu=false;navigationOpen=!navigationOpen})
                            if(custom!=null)DropdownMenuItem(text={Text("Edit group")},onClick={menu=false;editing=custom})
                            DropdownMenuItem(text={Text("Back toward now")},onClick={menu=false;shiftTime(-1800000)})
                            DropdownMenuItem(text={Text("Jump to now")},onClick={menu=false;start=System.currentTimeMillis()})
                            DropdownMenuItem(text={Text("Later")},onClick={menu=false;shiftTime(1800000)})
                        }
                    }
                    IconButton(onClick={searchDraft=query;searching=true},modifier=Modifier.width(36.dp)){
                        Icon(painterResource(R.drawable.rail_search),contentDescription="Search channels",tint=if(query.isEmpty())TvPalette.Text else TvPalette.Signal)
                    }
                    Text(DateTimeFormatter.ofPattern("EEE d MMM").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(start)),maxLines=1,style=MaterialTheme.typography.labelSmall)
                }
                repeat(3){Text(clock(start+it*1800000),Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)}
            }
            LazyColumn(Modifier.weight(1f),state=state.channelScroll,verticalArrangement=Arrangement.spacedBy(2.dp)){
                items(filtered,key={it.id}){c->
                    Row(Modifier.fillMaxWidth().height(rowHeight).clipToBounds(),horizontalArrangement=Arrangement.spacedBy(3.dp)){
                        Row(Modifier.width(170.dp)){
                            IconButton(onClick={savePreferences(preferences.copy(favorites=if(c.id in preferences.favorites)preferences.favorites-c.id else preferences.favorites+c.id))},modifier=Modifier.width(40.dp).semantics{contentDescription=if(c.id in preferences.favorites)"Remove ${c.name} from favorites" else "Add ${c.name} to favorites"}){Text(if(c.id in preferences.favorites)"★" else "☆",color=TvPalette.Accent)}
                            Box(Modifier.weight(1f)){GuideButton(c.name,flat=true,channelLabel=true,density=density,logo=c.logo,onLongPress={contextMenu(c,cache[c.id]?.second?.firstOrNull{it.start<=now&&it.end>now})},modifier=if(c.id==state.returnChannel.value)Modifier.focusRequester(returnFocus)else if(c.id==filtered.firstOrNull()?.id)Modifier.focusRequester(firstChannel)else Modifier,onFocused={state.focusedChannel.value=c;state.focusedProgramme.value=cache[c.id]?.second?.firstOrNull{it.start<=now && it.end>now}}){play(c)}}
                        }
                        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().clipToBounds().background(Color.Transparent)){
                            val width=maxWidth
                            val timelineFocus=remember{FocusRequester()}
                            var timelineFocused by remember{mutableStateOf(false)}
                            fun describeTime(){state.focusedChannel.value=c;state.focusedProgramme.value=cache[c.id]?.second?.firstOrNull{it.end>start}}
                            val pixels=with(LocalDensity.current){width.toPx()}
                            val dragState=rememberDraggableState{delta->shiftTime((-delta/pixels*5400000).toLong())}
                            Box(Modifier.fillMaxSize().focusRequester(timelineFocus).onFocusChanged{timelineFocused=it.isFocused;if(it.isFocused)describeTime()}.onPreviewKeyEvent{event->
                                if(event.type==KeyEventType.KeyDown && (event.key==Key.DirectionLeft || event.key==Key.DirectionRight)){
                                    val visibleProgrammes=cache[c.id]?.second.orEmpty().filter{it.end>start&&it.start<start+5400000}.sortedBy{it.start}
                                    val index=visibleProgrammes.indexOf(state.focusedProgramme.value)
                                    if((event.key==Key.DirectionRight&&index==visibleProgrammes.lastIndex)||(event.key==Key.DirectionLeft&&index==0&&start>System.currentTimeMillis()+1000)){
                                        shiftTime(if(event.key==Key.DirectionRight)1800000 else -1800000);describeTime();true
                                    }else false
                                }else false
                            }.guideSelectKeys(watch={play(c)},menu={contextMenu(c,state.focusedProgramme.value)}).draggable(dragState,Orientation.Horizontal).semantics{contentDescription="${c.name} programme timeline. Left and right browse times. Select plays live. Hold Select for actions."}){
                            guideCells(cache[c.id]?.second.orEmpty(),start,start+5400000).forEach{cell->
                                val left=(cell.start-start)/5400000f;val right=(cell.end-start)/5400000f;val p=cell.programme
                                Box(Modifier.offset(x=width*left).width(width*(right-left)).fillMaxHeight().padding(end=2.dp).clipToBounds()){
                                    if(p==null)Text(if(cache.containsKey(c.id))"No EPG data" else "Loading EPG…",Modifier.fillMaxSize().background(TvPalette.Track).padding(8.dp),color=TvPalette.Muted,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
                                    else GuideButton(p.title.ifBlank{"Untitled programme"},flat=true,onLongPress={contextMenu(c,p)},density=density,modifier=Modifier.fillMaxHeight(),metadata=if(artwork && width*(right-left)>=110.dp)metadata[programmeMetadataKey(p)]?.second else null,onFocused={state.focusedChannel.value=c;state.focusedProgramme.value=p}){play(c)}
                                }
                            }
                            if(now in start..start+5400000)Box(Modifier.offset(x=width*((now-start)/5400000f)).width(2.dp).fillMaxHeight().background(TvPalette.Signal))
                            }
                        }
                    }
                }
                if(filtered.isEmpty())item{Text(if(query.isNotEmpty())"No channels match your search. Open Search to change or clear it." else "No channels in this view. Edit the group to add channels.",Modifier.padding(20.dp))}
            }
            Text("${filtered.size} channels  ·  ↑↓ Browse  ·  ←→ Timeline  ·  OK Watch  ·  Hold OK Actions",color=TvPalette.Muted,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(top=6.dp))
            if(state.refreshMessage.value.isNotEmpty())Text(state.refreshMessage.value,style=MaterialTheme.typography.bodySmall)
            if(message.isNotEmpty())Text(message,style=MaterialTheme.typography.bodySmall)
          }
        }
    }
    }
    contextTarget?.let{(c,p)->
        AlertDialog(onDismissRequest={closeContext()},title={Text(c.name)},text={Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState())){
            p?.let{Text(it.title,style=MaterialTheme.typography.titleMedium)}
            TextButton(onClick={closeContext();play(c)}){Text("Watch channel live")}
            TextButton(onClick={closeContext(restore=selected!="favorites");savePreferences(preferences.copy(favorites=if(c.id in preferences.favorites)preferences.favorites-c.id else preferences.favorites+c.id))}){Text(if(c.id in preferences.favorites)"Remove from favorites" else "Add to favorites")}
            TextButton(onClick={closeContext();detailTarget=c to p}){Text("Programme details")}
            if(p==null||p.end>System.currentTimeMillis())TextButton(onClick={
                closeContext()
                context.startActivity(android.content.Intent(context,RecordingActivity::class.java).putExtra("account",repo.profile.accountKey()).putExtra("schedule",true).putExtra("channel",c.id).putExtra("title",p?.title?:c.name).putExtra("start",p?.start?:System.currentTimeMillis()).putExtra("end",p?.end?:System.currentTimeMillis()+3600000))
            }){Text(if(p!=null&&p.start>System.currentTimeMillis())"Schedule recording" else "Record")}
        }},confirmButton={TextButton(onClick={closeContext()}){Text("Close")}})
    }
    detailTarget?.let{(c,p)->
        fun closeDetails(){detailTarget=null;state.returnChannel.value=c.id}
        AlertDialog(onDismissRequest={closeDetails()},text={Column(Modifier.heightIn(max=340.dp).verticalScroll(rememberScrollState())){ProgrammeFeature(c,p,null,Modifier.fillMaxWidth(),repo.profile)}},confirmButton={TextButton(onClick={closeDetails()}){Text("Close")}})
    }
    if(globalSearch)SearchEverywhere(repo,channels,cache.mapValues{it.value.second},dismiss={globalSearch=false}){hit->
        globalSearch=false
        if(hit.kind.equals("recording",true))context.startActivity(android.content.Intent(context,RecordingActivity::class.java).putExtra("account",repo.profile.accountKey()))
        else hit.channel?.let{c->selected="all";query="";navigationOpen=false;hit.start?.let{start=maxOf(it,System.currentTimeMillis())};state.returnChannel.value=c.id;state.focusedChannel.value=c}
    }
    if(recentOpen)AlertDialog(onDismissRequest={recentOpen=false},title={Text("Recent channels")},text={
        val recent=experience.recent().mapNotNull{id->channels.find{it.id==id}}
        LazyColumn(Modifier.heightIn(max=280.dp)){if(recent.isEmpty())item{Text("Channels you watch will appear here.")};items(recent,key={it.id}){c->TextButton(onClick={recentOpen=false;play(c)}){Text(c.name)}}}
    },confirmButton={TextButton(onClick={recentOpen=false}){Text("Close")}})
    if(appearance)AppearanceStudio(repo.profile){appearance=false}
    if(searching){
        val searchFocus=remember{FocusRequester()}
        val keyboard=LocalSoftwareKeyboardController.current
        AlertDialog(onDismissRequest={searching=false},title={Text("Search channels")},text={
            OutlinedTextField(searchDraft,{searchDraft=it},singleLine=true,label={Text("Channel name")},modifier=Modifier.fillMaxWidth().focusRequester(searchFocus))
        },confirmButton={Button(onClick={query=searchDraft;keyboard?.hide();searching=false;scope.launch{state.channelScroll.scrollToItem(0)}}){Text("Search")}},dismissButton={Row{if(query.isNotEmpty())TextButton(onClick={query="";searchDraft="";keyboard?.hide();searching=false;scope.launch{state.channelScroll.scrollToItem(0)}}){Text("Clear search")};TextButton(onClick={keyboard?.hide();searching=false}){Text("Cancel")}}})
        LaunchedEffect(Unit){searchFocus.requestFocus();keyboard?.show()}
    }
    if(arranging){
        var draft by remember{mutableStateOf(preferences)}
        var committing by remember{mutableStateOf(false)}
        var saveError by remember{mutableStateOf("")}
        val draftRows=groupRows.sortedWith(compareBy<Triple<String,String,Int>>{draft.groupOrder.indexOf(it.first).let{n->if(n<0)Int.MAX_VALUE else n}}.thenBy{it.second.lowercase()}.thenBy{it.first})
        AlertDialog(onDismissRequest={if(!committing)arranging=false},title={Text("Manage groups")},text={Column{
        Text("Favorites stays pinned. Uncheck to hide a group, or move groups up and down. Done saves your changes.")
        LazyColumn(Modifier.height(300.dp)){items(draftRows,key={it.first}){g->val index=draftRows.indexOf(g)
            Row{
                Checkbox(checked=g.first !in draft.hiddenGroups,onCheckedChange={shown->draft=draft.copy(hiddenGroups=if(shown)draft.hiddenGroups-g.first else draft.hiddenGroups+g.first)},enabled=!committing,modifier=Modifier.semantics{contentDescription="Show ${g.second}"})
                Text(g.second,Modifier.weight(1f).padding(top=14.dp),maxLines=1,overflow=TextOverflow.Ellipsis)
                fun move(delta:Int){val ids=draftRows.map{it.first}.toMutableList();val id=ids.removeAt(index);ids.add(index+delta,id);draft=draft.copy(groupOrder=ids)}
                IconButton(onClick={move(-1)},enabled=index>0&&!committing,modifier=Modifier.semantics{contentDescription="Move ${g.second} up"}){Text("↑")}
                IconButton(onClick={move(1)},enabled=index<draftRows.lastIndex&&!committing,modifier=Modifier.semantics{contentDescription="Move ${g.second} down"}){Text("↓")}
            }
        }}
        if(saveError.isNotEmpty())Text(saveError)
    }},confirmButton={TextButton(enabled=!committing&&!saving,onClick={
        committing=true
        scope.launch{
            try{store.savePreferences(draft);preferences=draft;arranging=false;if(selected in draft.hiddenGroups)choose("favorites")}
            catch(e:Exception){saveError=e.message?:"Could not save groups. Try again."}
            finally{committing=false}
        }
    }){Text(if(committing)"Saving..." else "Done")}},dismissButton={TextButton(enabled=!committing,onClick={arranging=false}){Text("Cancel")}})
    }
    editing?.let{g->GroupEditor(g,channels,onDismiss={editing=null},onSave={name,ids->scope.launch{try{store.save(g.copy(name=name,channelIds=ids));groups=store.load();editing=null;message="Group saved"}catch(e:Exception){message=e.message?:"Could not save group"}}},onDelete={scope.launch{try{store.delete(g.id);groups=store.load();selected="all";editing=null}catch(e:Exception){message=e.message?:"Could not delete group"}}})}
}

@Composable private fun GuideButton(text: String, selected: Boolean=false, flat:Boolean=false, channelLabel:Boolean=false, modifier:Modifier=Modifier, metadata:org.json.JSONObject?=null, density:GuideDensity=GuideDensity.COMPACT, logo:String="", onLongPress:(()->Unit)?=null, onFocused:()->Unit={}, action: ()->Unit){
    var focused by remember{mutableStateOf(false)}
    val look=Appearance.current
    val fill=focused&&(flat||look.focus=="fill")
    val shape=RoundedCornerShape(if(flat)0.dp else look.radius.dp)
    val keys=if(onLongPress!=null)Modifier.guideSelectKeys(action,onLongPress)else Modifier
    Row(modifier.then(keys).fillMaxWidth().heightIn(min=36.dp).onFocusChanged{focused=it.isFocused;if(it.isFocused)onFocused()}.then(if(flat)Modifier.border(if(focused)2.dp else 0.dp,if(focused)TvPalette.Accent else Color.Transparent,shape) else Modifier.border(if(focused&&look.focus=="glow")3.dp else if(focused||look.borders=="strong")2.dp else 1.dp,if(focused)TvPalette.Accent else TvPalette.GuideBorder,shape)).background(if(fill)TvPalette.Accent else if(channelLabel)Color.Transparent else if(selected)TvPalette.Live else TvPalette.Programme,shape).combinedClickable(onClick=action,onLongClick=onLongPress,onLongClickLabel=if(onLongPress!=null)"Channel actions" else null,role=androidx.compose.ui.semantics.Role.Button).padding(horizontal=8.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
        if(logo.isNotBlank())ChannelLogo(logo,Modifier.width(28.dp).height(26.dp).padding(end=4.dp))
        val artwork=metadataArtwork(metadata)
        if(artwork!=null)MetadataArtwork(artwork,Modifier.width(24.dp).height(34.dp).padding(end=4.dp))
        Column(Modifier.weight(1f)){
            Text(text,color=if(fill)TvPalette.OnAccent else TvPalette.Text,maxLines=if(metadata==null)2 else 1,overflow=TextOverflow.Ellipsis,fontWeight=if(focused || selected)FontWeight.SemiBold else FontWeight.Normal,style=MaterialTheme.typography.bodyMedium,fontSize=(density.text-if(metadata==null)0 else 2).sp,lineHeight=(density.text+4).sp)
            if(metadata!=null)Text(listOf(episodeLabel(metadata),metadata.optString("year"),metadataNames(metadata,"genres")).filter{it.isNotBlank()}.joinToString(" · "),color=if(fill)TvPalette.OnAccent else TvPalette.Muted,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=10.sp,lineHeight=12.sp)
        }
    }
}

@Composable private fun GroupEditor(group: CustomGroup, channels: List<Channel>,onDismiss:()->Unit,onSave:(String,Set<String>)->Unit,onDelete:()->Unit){
    var name by remember{mutableStateOf(group.name)};var query by remember{mutableStateOf("")};var ids by remember{mutableStateOf(group.channelIds)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(group.id.isEmpty())"Create channel group" else "Edit channel group")},text={Column{
        TvTextInput(name,{name=it.take(80)},label={Text("Group name")},singleLine=true)
        TvTextInput(query,{query=it},label={Text("Search channels · ${ids.size}/500 selected")},singleLine=true)
        LazyColumn(Modifier.height(230.dp)){items(channels.filter{it.name.contains(query,true)},key={it.id}){c->
            Row{Checkbox(c.id in ids,{checked->if(!checked)ids=ids-c.id else if(ids.size<500)ids=ids+c.id});Text(c.name,Modifier.padding(top=14.dp),style=MaterialTheme.typography.bodySmall)}
        }}
    }},confirmButton={
        Button(onClick={onSave(name.trim(),ids)},enabled=name.isNotBlank()){Text("Save group")}
    },dismissButton={
        Row{
            if(group.id.isNotEmpty())TextButton(onClick=onDelete){Text("Delete")}
            TextButton(onClick=onDismiss){Text("Cancel")}
        }
    })
}
