package local.signaltv.app

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalContext

class PlaybackQuality(context:Context,profile:Profile){
 private val prefs=context.getSharedPreferences("playback-quality",Context.MODE_PRIVATE)
 private val key=profile.accountKey()
 fun get()=prefs.getString(key,"auto").takeIf{it in listOf("auto","original","data-saver")}?:"auto"
 fun set(value:String){require(value in listOf("auto","original","data-saver"));prefs.edit().putString(key,value).apply()}
}
fun companionQuality(value:String)=when(value){"data-saver"->"data-saver";else->"remux"}
@Composable fun PlaybackQualityDialog(profile:Profile,onDismiss:()->Unit){
 val context=LocalContext.current
 val settings=remember(profile){PlaybackQuality(context,profile)}
 var selected by remember{mutableStateOf(settings.get())}
 AlertDialog(onDismissRequest=onDismiss,title={Text("Playback quality")},text={Column{
  Text(if(profile.mode=="server")"Auto and Original preserve provider codecs for Android. Data Saver asks your server to convert to at most 480p; requires the updated server." else "Standalone Data Saver prefers lower-quality variants when the provider offers them. A fixed-quality stream cannot be reduced without a server.")
  for((value,label) in listOf("auto" to "Auto", "original" to "Original", "data-saver" to "Data Saver"))TextButton(onClick={selected=value;settings.set(value)}){Text((if(selected==value)"✓ " else "")+label)}
  Text("Applies to newly opened streams. Existing streams keep their quality.")
 }},confirmButton={TextButton(onClick=onDismiss){Text("Done")}})
}
