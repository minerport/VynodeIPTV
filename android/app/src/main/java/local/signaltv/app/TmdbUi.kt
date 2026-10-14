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
 var token by remember{mutableStateOf("")};var enabled by remember{mutableStateOf(false)};var configured by remember{mutableStateOf(false)};var status by remember{mutableStateOf("Loading…")};var busy by remember{mutableStateOf(false)}
 val server=profile?.mode=="server"
 LaunchedEffect(profile){try{val s=if(server)JSONObject(request(profile!!.base+"/api/v1/metadata/settings",profile.token))else withContext(Dispatchers.IO){secure.tmdb()};enabled=s.optBoolean("enabled");configured=if(server)s.optBoolean("configured")else s.optString("token").isNotBlank();status=if(configured)"TMDB connected" else "Not connected"}catch(_:Exception){status="Could not read metadata settings"}}
 AlertDialog(onDismissRequest={if(!busy)dismiss()},title={Text("Metadata · TMDB")},text={Column(Modifier.heightIn(max=350.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
  if(server)Text("Enrichment comes from your IPTV server. Manage its TMDB connection in the server WebUI → Server settings → Metadata.")else{
   Text("Optional movie and series artwork, descriptions and cast. Programme titles are sent to TMDB. Provider credentials are never sent.")
   TvTextInput(token,{token=it},label={Text("API Read Access Token")},placeholder={Text(if(configured)"Leave blank to keep saved token" else "Enter token from TMDB API settings")},visualTransformation=PasswordVisualTransformation())
   Row{Checkbox(enabled,{enabled=it},enabled=!busy);Text("Enable enrichment",Modifier.padding(top=12.dp))}
   Text("Get your token at themoviedb.org/settings/api",fontSize=12.sp)
  }
  Text(status,fontSize=12.sp)
  Text("Credits",style=MaterialTheme.typography.titleSmall)
  Image(painterResource(R.drawable.tmdb_logo),"TMDB",Modifier.width(70.dp).height(51.dp))
  Text(TMDB_NOTICE,fontSize=11.sp)
 }},confirmButton={if(server)TextButton(onClick=dismiss){Text("Close")}else Button(enabled=!busy,onClick={busy=true;scope.launch{try{val value=token.trim().ifBlank{withContext(Dispatchers.IO){secure.tmdb().optString("token")}};require(!enabled||value.isNotBlank()){"Enter a TMDB API Read Access Token"};require(value.length<=4096&&!value.contains('\n')&&!value.contains('\r')){"Invalid TMDB token"};service.save(value,enabled);token="";configured=value.isNotBlank();status="Settings saved"}catch(e:CancellationException){throw e}catch(e:Exception){status=e.message?:"Could not save connection"}finally{busy=false}}}){Text(if(busy)"Checking…" else "Test & save")}},dismissButton={Row{if(!server)TextButton(enabled=!busy,onClick={busy=true;scope.launch{try{service.disconnect();token="";configured=false;enabled=false;status="Disconnected"}finally{busy=false}}}){Text("Disconnect")};TextButton(enabled=!busy,onClick=dismiss){Text("Close")}}})
}

@Composable fun rememberProgrammeMetadata(profile:Profile?,p:Programme?):JSONObject?{
 val context=LocalContext.current;var value by remember(profile,p?.title,p?.year){mutableStateOf<JSONObject?>(null)}
 LaunchedEffect(profile,p?.title,p?.year){value=null;if(profile==null||p==null)return@LaunchedEffect;delay(400);try{value=TmdbMetadata.get(context).lookup(profile,p)}catch(e:CancellationException){throw e}catch(_:Exception){}}
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

fun programmeMetadataKey(p:Programme)=metadataTitle(cleanMetadataQuery(p.title,p.year).title)+"|"+cleanMetadataQuery(p.title,p.year).year
