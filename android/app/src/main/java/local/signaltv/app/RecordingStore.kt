package local.signaltv.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class LocalRecording(val id:String,val account:String,val channel:String,val channelName:String,val title:String,val start:Long,val end:Long,val storage:String,val limit:Int,val status:String="scheduled",val reason:String="",val parts:List<String> = emptyList(),val gaps:Int=0,val bytes:Long=0,val resumePart:Int=0,val resumeMs:Long=0) {
 fun json()=JSONObject().put("id",id).put("account",account).put("channel",channel).put("channelName",channelName).put("title",title).put("start",start).put("end",end).put("storage",storage).put("limit",limit).put("status",status).put("reason",reason).put("parts",JSONArray(parts)).put("gaps",gaps).put("bytes",bytes).put("resumePart",resumePart).put("resumeMs",resumeMs)
 companion object {fun from(j:JSONObject):LocalRecording {val p=j.optJSONArray("parts")?:JSONArray();return LocalRecording(j.getString("id"),j.getString("account"),j.getString("channel"),j.getString("channelName"),j.getString("title"),j.getLong("start"),j.getLong("end"),j.getString("storage"),j.getInt("limit"),j.optString("status","scheduled"),j.optString("reason"),(0 until p.length()).map{p.getString(it)},j.optInt("gaps"),j.optLong("bytes"),j.optInt("resumePart"),j.optLong("resumeMs"))}}
}
object RecordingStore {
 private fun prefs(c:Context)=c.getSharedPreferences("recordings",Context.MODE_PRIVATE)
 @Synchronized fun list(c:Context):List<LocalRecording>{val a=JSONArray(prefs(c).getString("jobs","[]"));return (0 until a.length()).map{LocalRecording.from(a.getJSONObject(it))}}
 @Synchronized private fun save(c:Context,jobs:List<LocalRecording>){check(prefs(c).edit().putString("jobs",JSONArray(jobs.map{it.json()}).toString()).commit()){"Cannot save recording schedule"}}
 @Synchronized fun update(c:Context,id:String,f:(LocalRecording)->LocalRecording){save(c,list(c).map{if(it.id==id)f(it)else it})}
 fun storage(c:Context)=prefs(c).getString("storage",null)
 fun storage(c:Context,value:String){check(prefs(c).edit().putString("storage",value).commit())}
 fun exactAllowed(c:Context)=Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
 @Synchronized fun add(c:Context,profile:Profile,channel:Channel,title:String,start:Long,end:Long,limit:Int):LocalRecording {
  val now=System.currentTimeMillis();require(end>now&&end>start&&end-start<=26*3600000L&&start<now+30*86400000L){"Choose a recording up to 24 hours long within 30 days"}
  require(exactAllowed(c)){"Allow Alarms & reminders in Android settings before scheduling recordings"}
  val storage=storage(c)?:error("Choose recording storage first")
  val job=LocalRecording(UUID.randomUUID().toString(),profile.accountKey(),channel.id,channel.name,title,start.coerceAtLeast(now),end,storage,limit)
  val windows=list(c).filter{it.account==job.account&&it.status in listOf("scheduled","recording")}.map{RecordingWindow(it.start,it.end)}
  require(!recordingConflict(windows,RecordingWindow(job.start,job.end),limit)){"Recording conflicts with this provider's connection limit"}
  save(c,list(c)+job);schedule(c);return job
 }
 fun pending(c:Context,id:String)=PendingIntent.getBroadcast(c,0,Intent(c,RecordingReceiver::class.java).setAction("record").setData(Uri.parse("vynode-recording:$id")),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 @Synchronized fun reconcile(c:Context){save(c,list(c).map{j->
  if(ProviderSlots.contains(j.id))j
  else if(j.status in listOf("recording","stopping","scheduled")){
   val recovered=j.parts.sumOf{value->runCatching{val uri=Uri.parse(value);if(uri.scheme=="file")java.io.File(uri.path!!).length()else c.contentResolver.openFileDescriptor(uri,"r")?.use{it.statSize.coerceAtLeast(0)}?:0}.getOrDefault(0)}
   when{j.status=="stopping"->j.copy(bytes=recovered,status=if(recovered>0)"partial" else "cancelled",reason="Stopped by user")
    j.end<=System.currentTimeMillis()->j.copy(bytes=recovered,status=if(recovered>0)"partial" else "failed",reason="Recording window elapsed while device was unavailable")
    j.status=="recording"->j.copy(bytes=recovered,status="scheduled",gaps=j.gaps+1,reason="Resuming interrupted recording")
    else->j}
  }else j
 })}

 fun schedule(c:Context){reconcile(c);if(!exactAllowed(c))return;val alarm=c.getSystemService(AlarmManager::class.java);list(c).filter{it.status in listOf("scheduled","recording")}.forEach{alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,maxOf(System.currentTimeMillis()+2000,it.start),pending(c,it.id))}}
 @Synchronized fun delete(c:Context,id:String){val j=list(c).find{it.id==id}?:return;require(j.status !in listOf("scheduled","recording","stopping")){"Stop recording before deleting"};j.parts.forEach{val uri=Uri.parse(it);if(uri.scheme=="file"){check(java.io.File(uri.path!!).let{f->!f.exists()||f.delete()})}else check(android.provider.DocumentsContract.deleteDocument(c.contentResolver,uri))};save(c,list(c).filter{it.id!=id});c.getSystemService(AlarmManager::class.java).cancel(pending(c,id))}
 fun cancel(c:Context,id:String){update(c,id){it.copy(status=if(it.status=="recording")"stopping" else "cancelled",reason="Stopped by user")};c.getSystemService(AlarmManager::class.java).cancel(pending(c,id))}
}
