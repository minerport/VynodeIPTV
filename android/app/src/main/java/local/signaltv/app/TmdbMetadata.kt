package local.signaltv.app

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

const val TMDB_NOTICE="This product uses the TMDB API but is not endorsed or certified by TMDB."
suspend fun tmdbRequest(token:String,path:String):JSONObject=withContext(Dispatchers.IO){
 val c=URL("https://api.themoviedb.org/3$path").openConnection() as HttpURLConnection
 try{c.instanceFollowRedirects=false;c.connectTimeout=8000;c.readTimeout=8000;c.setRequestProperty("Authorization","Bearer $token")
  val code=c.responseCode;check(code==200){if(code==401||code==403)"TMDB rejected the token" else if(code==429)"TMDB rate limit; try again later" else "TMDB unavailable"}
  val bytes=c.inputStream.use{it.readBytesLimited(2*1024*1024)};JSONObject(bytes.toString(Charsets.UTF_8))
 }catch(e:CancellationException){throw e}catch(e:IllegalStateException){throw e}catch(_:Exception){throw IllegalStateException("TMDB connection failed; provider guide retained")}finally{c.disconnect()}
}
fun java.io.InputStream.readBytesLimited(limit:Int):ByteArray{val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=read(buffer);if(n<0)break;check(out.size()+n<=limit){"Metadata response too large"};out.write(buffer,0,n)};return out.toByteArray()}

class TmdbMetadata private constructor(context:Context){
 private val secure=SecureProfile(context.applicationContext)
 private val file=File(context.filesDir,"tmdb-cache.json")
 private val mutex=Mutex()
 private var retryAt=0L
 private var entries:JSONObject?=null
 private fun cache():JSONObject{if(entries==null)entries=runCatching{if(file.length()>8*1024*1024)JSONObject() else JSONObject(file.readText())}.getOrDefault(JSONObject());return entries!!}
 suspend fun save(token:String,enabled:Boolean){if(enabled)tmdbRequest(token,"/configuration");mutex.withLock{withContext(Dispatchers.IO){secure.saveTmdb(JSONObject().put("token",token).put("enabled",enabled));retryAt=0}}}
 suspend fun disconnect(){mutex.withLock{withContext(Dispatchers.IO){secure.saveTmdb(JSONObject());entries=JSONObject();file.delete();retryAt=0}}}
 private suspend fun resolve(token:String,q:MetadataQuery):JSONObject?{
  val result=tmdbRequest(token,"/search/multi?include_adult=false&language=en-US&query="+android.net.Uri.encode(q.title));val rows=result.optJSONArray("results")?:JSONArray()
  val candidates=(0 until rows.length()).map{rows.getJSONObject(it)}.map{r->val tv=r.optString("media_type")=="tv";MetadataCandidate(r.optInt("id"),r.optString("media_type"),r.optString(if(tv)"name" else "title"),r.optString(if(tv)"original_name" else "original_title"),r.optString(if(tv)"first_air_date" else "release_date"),r.optBoolean("adult"))}
  val match=chooseMetadata(q,candidates,result.optInt("total_pages"))?:return null
  val d=tmdbRequest(token,"/${match.kind}/${match.id}?language=en-US&append_to_response=credits")
  fun image(name:String,size:String):String{val path=d.optString(name);return if(Regex("/[A-Za-z0-9_-]+\\.(jpg|png)").matches(path))"https://image.tmdb.org/t/p/$size$path" else ""}
  val genres=d.optJSONArray("genres")?:JSONArray();val cast=d.optJSONObject("credits")?.optJSONArray("cast")?:JSONArray()
  return JSONObject().put("id",match.id).put("kind",match.kind).put("title",match.title).put("year",match.date.take(4)).put("overview",d.optString("overview").take(6000)).put("poster",image("poster_path","w342")).put("backdrop",image("backdrop_path","w780"))
   .put("genres",JSONArray((0 until minOf(genres.length(),8)).map{genres.getJSONObject(it).optString("name")})).put("cast",JSONArray((0 until minOf(cast.length(),5)).map{cast.getJSONObject(it).optString("name")}))
 }
 suspend fun lookup(profile:Profile,p:Programme):JSONObject?{
  val q=cleanMetadataQuery(p.title,p.year);if(!metadataAllowed(q))return null
  if(profile.mode=="server"){
   repeat(30){val r=JSONObject(request(profile.base+"/api/v1/metadata/lookup",profile.token,"POST",JSONObject().put("title",q.title).put("year",q.year)));if(r.optString("state")!="pending")return r.optJSONObject("data");delay(2000)};return null
  }
  return mutex.withLock{withContext(Dispatchers.IO){
   val settings=secure.tmdb();if(!settings.optBoolean("enabled"))return@withContext null
   val token=settings.optString("token");if(token.isBlank())return@withContext null
   val key=metadataTitle(q.title)+"|"+q.year;val now=System.currentTimeMillis();val all=cache();val existing=all.optJSONObject(key)
   if(existing!=null&&existing.optLong("expires")>now)return@withContext existing.optJSONObject("data")
   if(now<retryAt)return@withContext null
   try{val data=resolve(token,q);all.put(key,JSONObject().put("data",data?:JSONObject.NULL).put("expires",now+(if(data==null)6*3600000L else 7*86400000L)))
    all.keys().asSequence().toList().filter{(all.optJSONObject(it)?.optLong("expires",0L)?:0L)<now}.forEach{all.remove(it)}
    while(all.length()>1000){all.remove(all.keys().next())};writeMetadata(file,all.toString());delay(500);data
   }catch(e:CancellationException){throw e}catch(_:Exception){retryAt=now+60000;null}
  }}
 }
 companion object{@Volatile private var instance:TmdbMetadata?=null;fun get(context:Context):TmdbMetadata=instance?:synchronized(this){instance?:TmdbMetadata(context.applicationContext).also{instance=it}}}
}
