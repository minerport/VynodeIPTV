package local.signaltv.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.ui.PlayerView

/** Keep intentional surface detachments out of the video stall detector. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun bindPlaybackView(view:PlayerView,engine:PlaybackEngine?){
    val previous=view.tag as? PlaybackEngine
    if(previous!==engine){previous?.surfaceDetached();engine?.surfaceAttached();view.tag=engine}
    // The engine can rebuild its player while retaining the same session and view.
    if(view.player!==engine?.player)view.player=engine?.player
}

@Composable fun PlaybackHealthDialog(panels:List<Pair<String,PlaybackEngine>>,catalog:String,onDismiss:()->Unit){
    AlertDialog(onDismissRequest=onDismiss,title={Text("Playback health")},text={
        LazyColumn(Modifier.heightIn(max=400.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            item{Text(catalog,style=MaterialTheme.typography.bodySmall)}
            if(panels.isEmpty())item{Text("No active streams. Open a channel to collect measurements.")}
            items(panels){(name,engine)->Column{
                Text(name,style=MaterialTheme.typography.titleMedium)
                Text(engine.diagnostics,style=MaterialTheme.typography.bodySmall)
            }}
            item{Text("Frame/decoder progress cannot detect a still image or silence encoded by the provider. Audio counters may be unavailable for passthrough.",style=MaterialTheme.typography.bodySmall)}
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("Close")}})
}
