package local.signaltv.app

import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun clock(value: Long)=DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))

class GuideUiState {
    val navigationOpen=mutableStateOf(true)
    val focusedChannel=mutableStateOf<Channel?>(null)
    val focusedProgramme=mutableStateOf<Programme?>(null)
    val selected=mutableStateOf("all")
    val query=mutableStateOf("")
    val start=mutableLongStateOf(System.currentTimeMillis()/1800000*1800000)
    val groups=mutableStateOf<List<CustomGroup>>(emptyList())
    val preferences=mutableStateOf(GuidePreferences())
    val cache=mutableStateMapOf<String,Pair<Long,List<Programme>>>()
    val channelScroll=LazyListState()
    val groupScroll=LazyListState()
}

@Composable fun GuideScreen(channels: List<Channel>, repo: Repository, state: GuideUiState, preview: @Composable (androidx.compose.ui.unit.Dp)->Unit = {}, onWall:()->Unit={}, onSettings:()->Unit={}, onRefresh:()->Unit={}, play: (Channel)->Unit) {
    val context=LocalContext.current
    val store=remember(repo.profile){Groups(context,repo.profile)}
    val scope=rememberCoroutineScope()
    var navigationOpen by state.navigationOpen
    val firstChannel=remember{FocusRequester()}
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
    val cache=state.cache
    val providerGroups=remember(channels){channels.groupBy { it.groupId }}
    val custom=groups.find { "custom:"+it.id==selected }
    val filtered=remember(channels,query,selected,groups,preferences.favorites){channels.filter { (selected=="all" || (selected=="favorites" && it.id in preferences.favorites) || (custom?.channelIds?.contains(it.id) ?: ("provider:"+it.groupId==selected))) && it.name.contains(query,true) }}
    val visible by remember(filtered){derivedStateOf {
        val first=(state.channelScroll.layoutInfo.visibleItemsInfo.firstOrNull()?.index?:0).coerceAtLeast(0)
        val last=state.channelScroll.layoutInfo.visibleItemsInfo.lastOrNull()?.index?:7
        filtered.drop((first-2).coerceAtLeast(0)).take((last-first+5).coerceIn(8,24))
    }}
    val groupRows=(listOf(Triple("all","All channels",channels.size))+groups.map{Triple("custom:"+it.id,it.name,it.channelIds.size)}+providerGroups.map{(id,list)->Triple("provider:"+id,list.first().groupName,list.size)}).sortedWith(compareBy<Triple<String,String,Int>>{preferences.groupOrder.indexOf(it.first).let{index->if(index<0)Int.MAX_VALUE else index}}.thenBy{it.second.lowercase()}.thenBy{it.first})
    fun savePreferences(next: GuidePreferences){if(saving)return;saving=true;scope.launch{try{store.savePreferences(next);preferences=next}catch(e:Exception){message=e.message?:"Could not save preferences"}finally{saving=false}}}
    fun choose(id:String){selected=id;navigationOpen=false;scope.launch{state.channelScroll.scrollToItem(0);focusGuide=true}}
    BackHandler(enabled=!navigationOpen && editing==null && !arranging){navigationOpen=true}
    LaunchedEffect(focusGuide,filtered){if(focusGuide && filtered.isNotEmpty()){withFrameNanos{};firstChannel.requestFocus();focusGuide=false}}
    LaunchedEffect(navigationOpen){if(navigationOpen){
        val index=groupRows.filter{it.first !in preferences.hiddenGroups}.indexOfFirst{it.first==selected}
        if(index>=0)state.groupScroll.scrollToItem(index)
        withFrameNanos{}
        if(selected=="favorites" || index>=0)groupFocus.requestFocus()
    }}
    LaunchedEffect(store){try{groups=store.load();preferences=store.preferences()}catch(e:Exception){message=e.message?:"Could not load groups"}}
    LaunchedEffect(Unit){while(true){delay(30000);now=System.currentTimeMillis()}}
    LaunchedEffect(visible.map{it.id},now/300000){
        val missing=visible.filter{System.currentTimeMillis()-(cache[it.id]?.first?:0)>=300000}
        if(repo is ServerRepository){
            try{repo.programmeBatch(missing).forEach{(id,entries)->cache[id]=System.currentTimeMillis() to entries};while(cache.size>300)cache.entries.minByOrNull{it.value.first}?.key?.let{cache.remove(it)}}
            catch(e:CancellationException){throw e}catch(_:Exception){message="Guide temporarily unavailable"}
            return@LaunchedEffect
        }
        kotlinx.coroutines.supervisorScope {
            val permits=kotlinx.coroutines.sync.Semaphore(3)
            missing.forEach{c->launch{
                permits.acquire()
                try{cache[c.id]=System.currentTimeMillis() to repo.programmes(c);if(cache.size>300)cache.entries.minByOrNull{it.value.first}?.key?.let{cache.remove(it)}}
                catch(e:CancellationException){throw e}catch(_:Exception){message="Some guide listings are unavailable. Channels can still play."}
                finally{permits.release()}
            }}
        }
    }
    Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
        if(navigationOpen)Column(Modifier.width(64.dp).fillMaxHeight().background(Color(0xFF202C3D)).padding(4.dp)){
            Text("TV",Modifier.padding(8.dp),style=MaterialTheme.typography.titleLarge)
            RailIconButton(R.drawable.rail_search,"Search channels",showLabel=!searching){searchDraft=query;searching=true}
            RailIconButton(R.drawable.rail_favorite,"Favorites"){choose("favorites")}
            RailIconButton(R.drawable.rail_wall,"Video wall",action=onWall)
            RailIconButton(R.drawable.rail_sync,"Refresh channels",action=onRefresh)
            Spacer(Modifier.weight(1f))
            RailIconButton(R.drawable.rail_settings,"Connection settings",action=onSettings)
        }
        if(navigationOpen)Column(Modifier.width(180.dp).fillMaxHeight().background(Color(0xCC1C2A3D)).padding(8.dp)){
            Text("CHANNEL GROUPS",style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(8.dp))
            GuideButton("★ Favorites · ${channels.count{it.id in preferences.favorites}}",selected=="favorites",modifier=if(selected=="favorites")Modifier.focusRequester(groupFocus)else Modifier){choose("favorites")}
            Row{TextButton(onClick={editing=CustomGroup("","",emptySet())}){Text("+ New")};TextButton(onClick={arranging=true}){Text("Manage")}}
            LazyColumn(state=state.groupScroll,verticalArrangement=Arrangement.spacedBy(3.dp)){
                items(groupRows.filter{it.first !in preferences.hiddenGroups},key={it.first}){g->GuideButton(g.second+" · "+g.third,selected==g.first,modifier=if(selected==g.first)Modifier.focusRequester(groupFocus)else Modifier){choose(g.first)}}
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()){
          val previewHeight=(maxHeight*0.28f).coerceIn(100.dp,180.dp)
          Column(Modifier.fillMaxSize()){
            preview(previewHeight)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                TextButton(onClick={navigationOpen=!navigationOpen}){Text(if(navigationOpen)"Hide groups" else "‹ Groups")}
                if(custom!=null)TextButton(onClick={editing=custom}){Text("Edit group")}
                TextButton(onClick={start-=5400000}){Text("‹ Earlier")};TextButton(onClick={start=System.currentTimeMillis()/1800000*1800000}){Text("Now")};TextButton(onClick={start+=5400000}){Text("Later ›")}
                Text(DateTimeFormatter.ofPattern("EEE d MMM").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(start)),Modifier.padding(top=13.dp))
            }
            Row(Modifier.fillMaxWidth().padding(vertical=6.dp)){
                Text(if(selected=="favorites")"Favorites" else groupRows.find{it.first==selected}?.second?:"CHANNEL",Modifier.width(170.dp),maxLines=1,style=MaterialTheme.typography.labelSmall)
                repeat(3){Text(clock(start+it*1800000),Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)}
            }
            LazyColumn(Modifier.weight(1f),state=state.channelScroll,verticalArrangement=Arrangement.spacedBy(4.dp)){
                items(filtered,key={it.id}){c->
                    Row(Modifier.fillMaxWidth().height(52.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)){
                        Row(Modifier.width(170.dp)){
                            IconButton(onClick={savePreferences(preferences.copy(favorites=if(c.id in preferences.favorites)preferences.favorites-c.id else preferences.favorites+c.id))},enabled=!saving,modifier=Modifier.width(40.dp).semantics{contentDescription=if(c.id in preferences.favorites)"Remove ${c.name} from favorites" else "Add ${c.name} to favorites"}){Text(if(c.id in preferences.favorites)"★" else "☆",color=Color(0xFFDCE6F2))}
                            Box(Modifier.weight(1f)){GuideButton(c.name,modifier=if(c.id==filtered.firstOrNull()?.id)Modifier.focusRequester(firstChannel)else Modifier,onFocused={state.focusedChannel.value=c;state.focusedProgramme.value=cache[c.id]?.second?.firstOrNull{it.start<=now && it.end>now}}){play(c)}}
                        }
                        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF25384F))){
                            val width=maxWidth;val entries=cache[c.id]?.second.orEmpty().filter{it.end>start && it.start<start+5400000}
                            if(entries.isEmpty())Text(if(cache.containsKey(c.id))"No programme information" else "Loading guide…",Modifier.padding(10.dp),style=MaterialTheme.typography.bodySmall)
                            entries.forEach{p->val left=((p.start-start).coerceAtLeast(0)/5400000f);val right=((p.end-start).coerceAtMost(5400000)/5400000f)
                                Box(Modifier.offset(x=width*left).width(width*(right-left)).fillMaxHeight().padding(end=2.dp)){GuideButton(p.title,p.start<=now && p.end>now,onFocused={state.focusedChannel.value=c;state.focusedProgramme.value=p}){play(c)}}
                            }
                            if(now in start..start+5400000)Box(Modifier.offset(x=width*((now-start)/5400000f)).width(2.dp).fillMaxHeight().background(Color(0xFFDCE6F2)))
                        }
                    }
                }
                if(filtered.isEmpty())item{Text("No channels in this view. Edit the group to add channels.",Modifier.padding(20.dp))}
            }
            Text("${filtered.size} channels · Scroll to browse",style=MaterialTheme.typography.bodySmall)
            if(message.isNotEmpty())Text(message,style=MaterialTheme.typography.bodySmall)
          }
        }
    }
    if(searching){
        val searchFocus=remember{FocusRequester()}
        val keyboard=LocalSoftwareKeyboardController.current
        AlertDialog(onDismissRequest={searching=false},title={Text("Search channels")},text={
            OutlinedTextField(searchDraft,{searchDraft=it},singleLine=true,label={Text("Channel name")},modifier=Modifier.fillMaxWidth().focusRequester(searchFocus))
        },confirmButton={Button(onClick={query=searchDraft;keyboard?.hide();searching=false;scope.launch{state.channelScroll.scrollToItem(0)}}){Text("Search")}},dismissButton={TextButton(onClick={keyboard?.hide();searching=false}){Text("Cancel")}})
        LaunchedEffect(Unit){searchFocus.requestFocus();keyboard?.show()}
    }
    if(arranging)AlertDialog(onDismissRequest={arranging=false},title={Text("Manage groups")},text={Column{
        Text("Favorites stays pinned. Uncheck to hide a group, or move groups up and down.")
        LazyColumn(Modifier.height(300.dp)){items(groupRows,key={it.first}){g->val index=groupRows.indexOf(g)
            Row{
                Checkbox(checked=g.first !in preferences.hiddenGroups,onCheckedChange={shown->savePreferences(preferences.copy(hiddenGroups=if(shown)preferences.hiddenGroups-g.first else preferences.hiddenGroups+g.first));if(!shown && selected==g.first)choose("favorites")},enabled=!saving,modifier=Modifier.semantics{contentDescription="Show ${g.second}"})
                Text(g.second,Modifier.weight(1f).padding(top=14.dp),maxLines=1,overflow=TextOverflow.Ellipsis)
                fun move(delta:Int){val ids=groupRows.map{it.first}.toMutableList();val id=ids.removeAt(index);ids.add(index+delta,id);savePreferences(preferences.copy(groupOrder=ids))}
                IconButton(onClick={move(-1)},enabled=index>0&&!saving,modifier=Modifier.semantics{contentDescription="Move ${g.second} up"}){Text("↑")}
                IconButton(onClick={move(1)},enabled=index<groupRows.lastIndex&&!saving,modifier=Modifier.semantics{contentDescription="Move ${g.second} down"}){Text("↓")}
            }
        }}
        if(message.isNotEmpty())Text(message)
    }},confirmButton={TextButton(onClick={arranging=false}){Text("Done")}})
    editing?.let{g->GroupEditor(g,channels,onDismiss={editing=null},onSave={name,ids->scope.launch{try{store.save(g.copy(name=name,channelIds=ids));groups=store.load();editing=null;message="Group saved"}catch(e:Exception){message=e.message?:"Could not save group"}}},onDelete={scope.launch{try{store.delete(g.id);groups=store.load();selected="all";editing=null}catch(e:Exception){message=e.message?:"Could not delete group"}}})}
}

@Composable private fun GuideButton(text: String, selected: Boolean=false, modifier:Modifier=Modifier, onFocused:()->Unit={}, action: ()->Unit){
    var focused by remember{mutableStateOf(false)}
    TextButton(onClick=action,modifier=modifier.fillMaxWidth().heightIn(min=48.dp).onFocusChanged{focused=it.isFocused;if(it.isFocused)onFocused()}.border(if(focused)2.dp else 0.dp,if(focused)Color(0xFFDCE6F2)else Color.Transparent).background(if(focused)Color(0xFFDCE8F5)else if(selected)Color(0xFF3B4553)else Color(0xFF25384F)),contentPadding=PaddingValues(horizontal=8.dp,vertical=4.dp)){
        Text(text,color=if(focused)Color(0xFF152234)else Color.White,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.fillMaxWidth())
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
