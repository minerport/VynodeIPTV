package local.signaltv.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun FullscreenOverlay(channel:Channel,programmes:List<Programme>,now:Long,channels:List<Channel>,recent:List<String>,cached:Map<String,List<Programme>>,onGuide:()->Unit,onTune:(Channel)->Unit,profile:Profile?,video:@Composable ()->Unit){
    var intro by remember(channel.id){mutableStateOf(true)}
    var menu by remember(channel.id){mutableStateOf(false)}
    var history by remember{mutableStateOf(false)}
    val context=LocalContext.current
    val scores=remember(profile?.accountKey()){profile?.let{LiveScoreState(context,it)}}
    val screen=remember{FocusRequester()}
    val guide=remember{FocusRequester()}
    LaunchedEffect(channel.id){intro=true;delay(2750);intro=false}
    LaunchedEffect(menu,channel.id,scores?.editing){if(scores?.editing!=true){if(menu)guide.requestFocus() else screen.requestFocus()}}
    BackHandler(menu){menu=false}
    val programme=currentProgramme(programmes,now)
    val next=programmes.filter{it.start>now}.minByOrNull{it.start}
    fun time(value:Long)=DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))
    Box(Modifier.fillMaxSize().background(Color.Black)){
        video()
        Box(Modifier.fillMaxSize().focusRequester(screen).onPreviewKeyEvent{event->
            if(!menu&&scores?.editing!=true&&event.key==Key.DirectionUp){
                if(event.type==KeyEventType.KeyUp)scores?.toggle();true
            }else if(!menu&&(event.key==Key.DirectionCenter||event.key==Key.Enter||event.key==Key.NumPadEnter)){
                if(event.type==KeyEventType.KeyUp){menu=true;intro=false};true
            }else false
        }.focusable().clickable{menu=true;intro=false})
        if(profile!=null&&scores!=null)LiveScoreLayer(profile,channel,programme?.title.orEmpty(),scores,intro||menu)
        if(intro||menu){
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xBB000000),Color.Transparent))).padding(24.dp),horizontalArrangement=Arrangement.SpaceBetween){
                Text(channel.groupName,color=Color.White)
                Text(time(now),color=Color.White)
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xEE000000)))).padding(24.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    if(channel.logo.isNotBlank())ChannelLogo(channel.logo,Modifier.width(76.dp).height(54.dp).padding(end=12.dp))
                    Column(Modifier.weight(1f)){
                        Text(programme?.title?:"No EPG data",color=Color.White,style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(channel.name+(programme?.let{"  ·  ${time(it.start)} – ${time(it.end)}"}?:""),color=Color.White,style=MaterialTheme.typography.bodySmall)
                        if(!menu&&!programme?.description.isNullOrBlank())Text(programme!!.description,color=Color.White,maxLines=3,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
                        next?.let{Text("Next · ${time(it.start)}  ${it.title}",color=Color.LightGray,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)}
                    }
                }
                programme?.let{LinearProgressIndicator(progress={((now-it.start).toFloat()/(it.end-it.start).coerceAtLeast(1)).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().padding(vertical=10.dp))}
                if(menu){
                    val choices=if(history)recent.mapNotNull{id->channels.find{it.id==id}} else (recent.mapNotNull{id->channels.find{it.id==id}}+channels.filter{it.groupId==channel.groupId}).distinctBy{it.id}.take(30)
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        item{Button(onClick=onGuide,modifier=Modifier.focusRequester(guide).height(76.dp)){Text("TV guide")}}
                        item{Button(onClick={history=!history},modifier=Modifier.height(76.dp)){Text(if(history)"Channels" else "History")}}
                        if(scores!=null)item{Button(onClick={menu=false;intro=false;scores.editing=true},modifier=Modifier.height(76.dp)){Text("Live scores")}}
                        items(choices,key={it.id}){c->OutlinedButton(onClick={menu=false;onTune(c)},modifier=Modifier.width(160.dp).height(76.dp)){
                            Column{Text(c.name,maxLines=1,overflow=TextOverflow.Ellipsis);Text(currentProgramme(cached[c.id].orEmpty(),now)?.title?:"No EPG data",maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)}
                        }}
                    }
                }
            }
        }
    }
}
