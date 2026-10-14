package local.signaltv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun MediaHeader(){
    var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(Unit){while(true){delay(30000);now=System.currentTimeMillis()}}
    Row(Modifier.fillMaxWidth().padding(bottom=12.dp),verticalAlignment=Alignment.CenterVertically){
        Text("V",color=TvPalette.Signal,fontSize=22.sp,fontWeight=FontWeight.Black)
        Text("VYNODE",Modifier.padding(start=10.dp),fontSize=14.sp,letterSpacing=3.sp,fontWeight=FontWeight.Bold)
        Text("LIVE TV",Modifier.padding(start=18.dp),color=TvPalette.Muted,fontSize=10.sp,letterSpacing=2.sp)
        Spacer(Modifier.weight(1f))
        Text(DateTimeFormatter.ofPattern("EEE, MMM d  •  h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(now)),color=TvPalette.Muted,fontSize=11.sp)
    }
}

@Composable fun ProgrammeFeature(channel:Channel?,programme:Programme?,playing:Channel?,modifier:Modifier=Modifier,profile:Profile?=null){
    val metadata=rememberProgrammeMetadata(profile,programme)
    var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(Unit){while(true){delay(30000);now=System.currentTimeMillis()}}
    val live=programme!=null && programme.start<=now && programme.end>now
    Column(modifier.padding(vertical=4.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Text(if(live)"● LIVE" else if(programme==null)"DISCOVER" else if(programme.end<=now)"EARLIER" else "UP NEXT",
                Modifier.background(TvPalette.Signal.copy(alpha=.12f),RoundedCornerShape(4.dp)).padding(horizontal=8.dp,vertical=4.dp),
                color=TvPalette.Signal,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
            Text(channel?.groupName?:"YOUR TV. YOUR WAY.",color=TvPalette.Muted,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        Text(programme?.title?:channel?.name?:"Find your next great watch.",fontSize=26.sp,lineHeight=29.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
        if(programme!=null){
            val time=DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
            Text(time.format(Instant.ofEpochMilli(programme.start))+" – "+time.format(Instant.ofEpochMilli(programme.end))+"  ·  "+((programme.end-programme.start)/60000)+" min",color=TvPalette.Muted,fontSize=11.sp)
            if(live)LinearProgressIndicator(progress={((now-programme.start).toFloat()/(programme.end-programme.start)).coerceIn(0f,1f)},modifier=Modifier.widthIn(max=240.dp).fillMaxWidth().height(3.dp),color=TvPalette.Signal,trackColor=TvPalette.Line)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            metadata?.optString("poster")?.takeIf{safeMetadataArtwork(it)}?.let{MetadataArtwork(it,Modifier.width(55.dp).height(82.dp))}
            Column(Modifier.weight(1f)){
                if(metadata!=null)Text(listOf(metadata.optString("year"),metadataNames(metadata,"genres")).filter{it.isNotBlank()}.joinToString(" · "),color=TvPalette.Muted,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(programme?.description?.takeIf{it.isNotBlank()}?:metadata?.optString("overview")?.takeIf{it.isNotBlank()}?:if(channel==null)"Explore your channels, favorite the ones you love, and settle in." else if(programme==null)"No EPG data for this channel." else channel.name,
                    color=TvPalette.Muted,fontSize=12.sp,lineHeight=17.sp,maxLines=3,overflow=TextOverflow.Ellipsis)
                if(metadata!=null)Text("TMDB · "+(if(metadata.optString("kind")=="tv")"Series information" else "Movie information")+metadataNames(metadata,"cast").takeIf{it.isNotBlank()}?.let{" · $it"}.orEmpty(),fontSize=10.sp,color=TvPalette.Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
        if(playing!=null)Text("WATCHING  ·  "+playing.name,color=TvPalette.Text.copy(alpha=.8f),fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}
