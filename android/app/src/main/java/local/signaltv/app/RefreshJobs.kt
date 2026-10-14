package local.signaltv.app

import android.app.job.*
import android.content.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RefreshPreferences(context:Context,p:Profile){
 private val prefs=context.getSharedPreferences("refresh-"+p.accountKey(),Context.MODE_PRIVATE)
 val channels get()=prefs.getBoolean("channels",false)
 val epg get()=prefs.getBoolean("epg",false)
 fun set(channels:Boolean,epg:Boolean){check(prefs.edit().putBoolean("channels",channels).putBoolean("epg",epg).commit())}
 fun due(kind:String)=System.currentTimeMillis()-prefs.getLong(kind+"-success",0)>=86400000
 fun success(kind:String){prefs.edit().putLong(kind+"-success",System.currentTimeMillis()).apply()}
}
object RefreshJobs {
 private val mutex=Mutex()
 fun schedule(context:Context){val scheduler=context.getSystemService(JobScheduler::class.java);if(scheduler.getPendingJob(4101)==null)scheduler.schedule(JobInfo.Builder(4101,ComponentName(context,DailyRefreshService::class.java)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(60*60*1000L).setPersisted(true).build())}
 suspend fun run(context:Context)=mutex.withLock{for(p in SecureProfile(context).accounts()){
  val policy=RefreshPreferences(context,p);val repo:Repository=if(p.mode=="server")ServerRepository(p)else XtreamRepository(p,context)
  if(policy.channels && policy.due("channels"))try{val entries=repo.refreshChannels();CatalogCache(context,p).save(entries);policy.success("channels")}catch(e:CancellationException){throw e}catch(_:Exception){}
  if(policy.epg && policy.due("epg"))try{val entries=CatalogCache(context,p).load().ifEmpty{repo.channels()};repo.refreshEpg(entries);policy.success("epg")}catch(e:CancellationException){throw e}catch(_:Exception){}
 }}
}
class DailyRefreshService:JobService(){
 private var job:Job?=null
 override fun onStartJob(params:JobParameters):Boolean{job=CoroutineScope(Dispatchers.IO).launch{try{RefreshJobs.run(applicationContext)}catch(e:CancellationException){throw e}catch(_:Exception){};withContext(Dispatchers.Main){jobFinished(params,false)}};return true}
 override fun onStopJob(params:JobParameters):Boolean{job?.cancel();return true}
}
