package local.signaltv.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject

@Composable fun TmdbSettings(profile:Profile?,dismiss:()->Unit){
 val context=LocalContext.current;val service=remember{TmdbMetadata.get(context)};val secure=remember{SecureProfile(context)};val scope=rememberCoroutineScope()
 var token by remember{mutableStateOf("")};var enabled by remember{mutableStateOf(false)};var background by remember{mutableStateOf(true)};var configured by remember{mutableStateOf(false)};var status by remember{mutableStateOf("Loading…")};var busy by remember{mutableStateOf(false)}
 val server=profile?.mode=="server"
 LaunchedEffect(profile){try{val s=if(server)JSONObject(request(profile!!.base+"/api/v1/metadata/settings",profile.token))else withContext(Dispatchers.IO){secure.tmdb()};enabled=s.optBoolean("enabled");background=s.optBoolean("background",true);configured=if(server)s.optBoolean("configured")else s.optString("token").isNotBlank();status=if(configured)"TMDB connected" else "Not connected"}catch(_:Exception){status="Could not read metadata settings"}}
 AlertDialog(onDismissRequest={if(!busy)dismiss()},title={Text("Metadata · TMDB")},text={Column(Modifier.heightIn(max=350.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
  if(server)Text("Enrichment comes from your IPTV server. Manage its TMDB connection in the server WebUI → Server settings → Metadata.")else{
   Text("Optional movie and series artwork, descriptions and cast. Programme titles are sent to TMDB. Provider credentials are never sent.")
   TvTextInput(token,{token=it},label={Text("API key or Read Access Token")},placeholder={Text(if(configured)"Leave blank to keep saved token" else "Use the shorter API key from TMDB settings")},visualTransformation=PasswordVisualTransformation())
   TextButton(enabled=!busy,onClick={
    val clipboard=context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val pasted=clipboard.primaryClip?.takeIf{it.itemCount>0}?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
    if(pasted.isBlank())status="Clipboard is empty on this device" else if(pasted.length>4096||pasted.contains('\n')||pasted.contains('\r'))status="Clipboard does not contain a valid key" else {token=pasted;status="Key pasted · choose Test & save"}
   }){Text("Paste from clipboard")}
   Text("The shorter API Key (v3 auth) works too. In companion mode, configure TMDB once in your server WebUI instead.",fontSize=12.sp)
   Row{Checkbox(enabled,{enabled=it},enabled=!busy);Text("Enable enrichment",Modifier.padding(top=12.dp))}
   Row{Checkbox(background,{background=it},enabled=!busy);Text("Cache upcoming 24-hour guide",Modifier.padding(top=12.dp))}
   Text("Get your token at themoviedb.org/settings/api",fontSize=12.sp)
  }
  Text(status,fontSize=12.sp)
  Text("Credits",style=MaterialTheme.typography.titleSmall)
  Image(painterResource(R.drawable.tmdb_logo),"TMDB",Modifier.width(70.dp).height(51.dp))
  Text(TMDB_NOTICE,fontSize=11.sp)
 }},confirmButton={if(server)TextButton(onClick=dismiss){Text("Close")}else Button(enabled=!busy,onClick={busy=true;scope.launch{try{val value=token.trim().ifBlank{withContext(Dispatchers.IO){secure.tmdb().optString("token")}};require(!enabled||value.isNotBlank()){"Enter a TMDB API key or Read Access Token"};require(value.length<=4096&&!value.contains('\n')&&!value.contains('\r')){"Invalid TMDB token"};service.save(value,enabled,background);token="";configured=value.isNotBlank();status="Settings saved"}catch(e:CancellationException){throw e}catch(e:Exception){status=e.message?:"Could not save connection"}finally{busy=false}}}){Text(if(busy)"Checking…" else "Test & save")}},dismissButton={Row{if(!server)TextButton(enabled=!busy,onClick={busy=true;scope.launch{try{service.disconnect();token="";configured=false;enabled=false;status="Disconnected"}finally{busy=false}}}){Text("Disconnect")};TextButton(enabled=!busy,onClick=dismiss){Text("Close")}}})
}

@Composable fun rememberProgrammeMetadata(profile:Profile?,p:Programme?):JSONObject?{
 val context=LocalContext.current;var value by remember(profile,p?.let{programmeMetadataKey(it)}){mutableStateOf<JSONObject?>(null)}
 LaunchedEffect(profile,p?.let{programmeMetadataKey(it)}){value=null;if(profile==null||p==null)return@LaunchedEffect;delay(400);try{value=TmdbMetadata.get(context).lookup(profile,p)}catch(e:CancellationException){throw e}catch(_:Exception){}}
 return value
}

private val artworkRequests=Semaphore(2)
private val artworkCache=object:android.util.LruCache<String,android.graphics.Bitmap>(8*1024*1024){
 override fun sizeOf(key:String,value:android.graphics.Bitmap)=value.byteCount
}
@Composable fun MetadataArtwork(url:String,modifier:Modifier=Modifier){
 var bitmap by remember(url){mutableStateOf<android.graphics.Bitmap?>(artworkCache.get(url))}
 LaunchedEffect(url){if(!safeMetadataArtwork(url)||bitmap!=null)return@LaunchedEffect;bitmap=artworkRequests.withPermit{withContext(Dispatchers.IO){runCatching{val c=java.net.URL(url).openConnection() as java.net.HttpURLConnection;try{c.instanceFollowRedirects=false;c.connectTimeout=5000;c.readTimeout=5000;check(c.responseCode==200);val bytes=c.inputStream.use{it.readBytesLimited(2*1024*1024)};BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=2})?.also{artworkCache.put(url,it)}}finally{c.disconnect()}}.getOrNull()}}}
 bitmap?.let{Image(it.asImageBitmap(),"Programme artwork from TMDB",modifier)}
}

fun metadataNames(data:JSONObject?,key:String):String {val a=data?.optJSONArray(key)?:return "";return (0 until a.length()).joinToString(", "){a.optString(it)}}

fun programmeMetadataKey(p:Programme)=metadataKey(programmeQuery(p))

fun episodeLabel(data:JSONObject?):String=if((data?.optInt("episode")?:0)>0)"S%02dE%02d".format(data!!.optInt("season"),data.optInt("episode"))+data.optString("episodeTitle").takeIf{it.isNotBlank()}?.let{" · $it"}.orEmpty() else ""
fun metadataArtwork(data:JSONObject?):String?=listOf("still","poster","backdrop").mapNotNull{data?.optString(it)}.firstOrNull{safeMetadataArtwork(it)}
