package local.signaltv.app

import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class RecordingReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){
 if(i.action==Intent.ACTION_BOOT_COMPLETED||i.action==Intent.ACTION_MY_PACKAGE_REPLACED){RecordingStore.schedule(c);return}
 try{c.startForegroundService(Intent(c,RecordingService::class.java))}catch(_:Exception){/* Durable jobs are retried when the user opens the app. */}
}}

class RecordingService:Service(){
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private val active=mutableSetOf<String>()
 private val connections=java.util.concurrent.ConcurrentHashMap<String,HttpURLConnection>()
 private var wake:PowerManager.WakeLock?=null
 override fun onBind(i:Intent?)=null
 override fun onCreate(){super.onCreate();val manager=getSystemService(NotificationManager::class.java);manager.createNotificationChannel(NotificationChannel("recordings","Recordings",NotificationManager.IMPORTANCE_LOW));val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE);startForeground(52,Notification.Builder(this,"recordings").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("VynodeIPTV recording").setContentText("Recordings continue independently of playback").setContentIntent(open).setOngoing(true).build());wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"VynodeIPTV:DVR").apply{acquire(27*3600000L)}}
 override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int {
  synchronized(active){RecordingStore.list(this).filter{it.status in listOf("scheduled","recording")&&it.start<=System.currentTimeMillis()&&it.id !in active}.forEach{j->active+=j.id;scope.launch{try{capture(j)}catch(_:CancellationException){RecordingStore.update(this@RecordingService,j.id){it.copy(status="scheduled",gaps=it.gaps+1,reason="Recording interrupted; waiting to resume")}}catch(_:Exception){finish(j.id,"Recording failed; check storage and provider")}finally{ProviderSlots.release(j.id);synchronized(active){active-=j.id;if(active.isEmpty()){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}}}}};if(active.isEmpty())stopSelf()};return START_STICKY
 }
 override fun onDestroy(){scope.cancel();connections.values.forEach{it.disconnect()};if(wake?.isHeld==true)wake?.release();super.onDestroy()}
 private fun finish(id:String,reason:String){RecordingStore.update(this,id){it.copy(status=if(it.bytes==0L)"failed" else if(reason.isNotEmpty()||it.gaps>0)"partial" else "completed",reason=reason)}}
 private fun part(job:LocalRecording):Uri {
  val name="${job.id}-${System.currentTimeMillis()}.ts"
  if(job.storage=="internal"){val dir=File(filesDir,"recordings");check(dir.exists()||dir.mkdirs());check(dir.usableSpace>512*1024*1024L){"Storage full"};return Uri.fromFile(File(dir,name))}
  val tree=Uri.parse(job.storage);val parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));return DocumentsContract.createDocument(contentResolver,parent,"video/mp2t",name)?:error("Recording folder unavailable")
 }
 private fun open(address:String,p:Profile):HttpURLConnection {
  var url=address
  repeat(6){
   val u=URL(url);require(u.protocol=="https"||(u.protocol=="http"&&p.allowHttp));val c=u.openConnection() as HttpURLConnection;c.connectTimeout=12000;c.readTimeout=12000;c.instanceFollowRedirects=false
   val code=try{c.responseCode}catch(e:Exception){c.disconnect();if(p.httpFallback&&p.allowHttp&&transportFailure(e)&&u.host==URL(p.base).host){val fallback=fallbackAddress(url);if(fallback!=null){url=fallback;return@repeat}};throw e}
   if(code in 300..399){val location=c.getHeaderField("Location");c.disconnect();require(!location.isNullOrBlank());url=URL(u,location).toString()}
   else {if(code !in 200..299){c.disconnect();error("Provider rejected stream")};return c}
  };error("Too many redirects")
 }
 private suspend fun capture(initial:LocalRecording){
  val p=SecureProfile(this).accounts().find{it.accountKey()==initial.account}?:run{finish(initial.id,"Saved provider was removed");return}
  if(System.currentTimeMillis()>=initial.end){finish(initial.id,"Recording window elapsed");return}
  if(!ProviderSlots.acquire(initial.id,initial.account,initial.limit)){finish(initial.id,"Provider connection limit reached; live streams preserved");return}
  RecordingStore.update(this,initial.id){it.copy(status="recording",gaps=it.gaps+if(it.status=="recording")1 else 0)}
  // Xtream's transport-stream endpoint is used for durable independent parts.
  val address=p.base+"/live/"+Uri.encode(p.username)+"/"+Uri.encode(p.password)+"/"+Uri.encode(initial.channel)+".ts"
  var attempts=0;var reason=""
  while(currentCoroutineContext().isActive&&System.currentTimeMillis()<initial.end){
   val job=RecordingStore.list(this).find{it.id==initial.id}?:return
   if(job.status=="stopping"){reason="Stopped by user";break}
   var connection:HttpURLConnection?=null
   var watchdog:Job?=null
   try{
    connection=open(address,p)
    connections[job.id]=connection
    watchdog=scope.launch{while(isActive){delay(500);if(System.currentTimeMillis()>=initial.end||RecordingStore.list(this@RecordingService).find{it.id==job.id}?.status=="stopping"){connections[job.id]?.disconnect();break}}}
    connection.inputStream.buffered().use{input->
     input.mark(564);val probe=ByteArray(564);var read=0;while(read<probe.size){val n=input.read(probe,read,probe.size-read);if(n<0)break;read+=n};input.reset()
     require(read==564&&probe[0]==0x47.toByte()&&probe[188]==0x47.toByte()&&probe[376]==0x47.toByte()){"Provider did not return MPEG-TS"}
     val buffer=ByteArray(188*348)
     while(System.currentTimeMillis()<initial.end&&currentCoroutineContext().isActive){
      val current=RecordingStore.list(this@RecordingService).find{it.id==job.id}?:return
      if(current.status=="stopping"){reason="Stopped by user";break}
      val uri=part(job);RecordingStore.update(this@RecordingService,job.id){it.copy(parts=it.parts+uri.toString())}
      val descriptor=if(uri.scheme=="file")ParcelFileDescriptor.open(File(uri.path!!),ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE) else contentResolver.openFileDescriptor(uri,"w")?:error("Storage unavailable")
      ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use{out->
       val initialFree=android.system.Os.fstatvfs(out.fd);check(initialFree.f_bavail*initialFree.f_bsize>512*1024*1024L){"Storage full"}
       var size=0L;var totalPart=0L;var lastSave=System.currentTimeMillis()
       try{
       while(totalPart<64*1024*1024L&&System.currentTimeMillis()<initial.end&&currentCoroutineContext().isActive){
        if(RecordingStore.list(this@RecordingService).find{it.id==job.id}?.status=="stopping"){reason="Stopped by user";break}
        // Packet-aligned chunks allow subsequent files to be decoded independently.
        var n=0;while(n<buffer.size){val got=input.read(buffer,n,buffer.size-n);if(got<0)throw java.io.EOFException();n+=got}
        out.write(buffer,0,n);size+=n;totalPart+=n
        if(System.currentTimeMillis()-lastSave>=5000){out.flush();out.fd.sync();val free=android.system.Os.fstatvfs(out.fd);check(free.f_bavail*free.f_bsize>128*1024*1024L){"Storage full"};RecordingStore.update(this@RecordingService,job.id){it.copy(bytes=it.bytes+size)};size=0;lastSave=System.currentTimeMillis()}
       }}finally{out.flush();out.fd.sync();RecordingStore.update(this@RecordingService,job.id){it.copy(bytes=it.bytes+size)}}
      }
      if(reason.isNotEmpty())break
     }
    }
    if(reason.isNotEmpty()||System.currentTimeMillis()>=initial.end)break
   }catch(e:CancellationException){throw e}catch(_:Exception){
    if(RecordingStore.list(this).find{it.id==job.id}?.status=="stopping"){reason="Stopped by user";break}
    if(System.currentTimeMillis()>=initial.end)break
    currentCoroutineContext().ensureActive();attempts++;RecordingStore.update(this,job.id){it.copy(gaps=it.gaps+1,reason="Stream or storage interrupted")};if(attempts>=5){reason="Stopped after five stream/storage failures; retained available footage";break};delay((1000L shl attempts).coerceAtMost(16000))}finally{watchdog?.cancel();connections.remove(job.id);connection?.disconnect()}
  }
  currentCoroutineContext().ensureActive();finish(initial.id,reason)
 }
}
