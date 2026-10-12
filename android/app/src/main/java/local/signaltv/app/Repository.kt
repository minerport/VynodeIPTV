package local.signaltv.app

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Channel(val id: String, val name: String, val epgId: String = "", val groupId: String = "other", val groupName: String = "Other channels")
data class Playback(val url: String, val sessionId: String? = null, val generation: Int = 0, val state: String = "playing", val reason: String = "")
data class Profile(val mode: String, val base: String, val token: String = "", val username: String = "", val password: String = "", val format: String = "m3u8", val allowHttp: Boolean = false, val streamProfile: String = "remux") {
    fun json() = JSONObject().put("mode",mode).put("base",base).put("token",token).put("username",username).put("password",password).put("format",format).put("allowHttp",allowHttp).put("streamProfile",streamProfile)
    companion object { fun from(j: JSONObject) = Profile(j.getString("mode"),j.getString("base"),j.optString("token"),j.optString("username"),j.optString("password"),j.optString("format","m3u8"),j.optBoolean("allowHttp"),j.optString("streamProfile","remux")) }
}
fun validateBase(value: String, allowHttp: Boolean): String {
    val u = Uri.parse(value.trim().trimEnd('/'))
    require(u.host != null && u.userInfo == null && u.query == null && u.fragment == null) { "Enter a server URL without credentials or query parameters" }
    require(u.scheme == "https" || (u.scheme == "http" && allowHttp)) { "Use HTTPS or explicitly enable HTTP" }
    return u.toString()
}
suspend fun request(url: String, token: String = "", method: String = "GET", body: JSONObject? = null): String = withContext(Dispatchers.IO) {
    val c = URL(url).openConnection() as HttpURLConnection
    try {
        c.connectTimeout = 12000; c.readTimeout = 20000; c.requestMethod = method; c.instanceFollowRedirects = false
        if (token.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type","application/json");c.outputStream.use { it.write(body.toString().toByteArray()) } }
        val status = c.responseCode
        require(status in 200..299) { if(status==409) "Connection limit reached. Remove a panel or check the server provider limit." else "Server returned HTTP $status" }
        c.inputStream.use { stream ->
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true){val n=stream.read(buffer);if(n<0)break;require(output.size()+n<=32*1024*1024){"Response too large"};output.write(buffer,0,n)}
            val bytes = output.toByteArray()
            bytes.toString(Charsets.UTF_8)
        }
    } catch (e: IllegalArgumentException) { throw e } catch (_: Exception) { throw IllegalStateException("Connection failed. Check the address, network and TLS settings.") } finally { c.disconnect() }
}
interface Repository {
    val profile: Profile
    val connectionLimit: Int get()=6
    suspend fun channels(): List<Channel>
    suspend fun play(channel: Channel): Playback
    suspend fun heartbeat(id: String): Playback
    suspend fun stop(id: String)
    suspend fun programmes(channel: Channel): List<Programme>
    suspend fun guide(channel: Channel): String = programmes(channel).take(3).joinToString("\n") { it.title }.ifEmpty { "No programme information" }
}
class ServerRepository(override val profile: Profile): Repository {
    private suspend fun api(path: String, method: String = "GET", body: JSONObject? = null) = request(profile.base + "/api/v1" + path, profile.token,method,body)
    override suspend fun channels(): List<Channel> { val a=JSONArray(api("/channels"));return (0 until a.length()).map { a.getJSONObject(it).let { Channel(it.getString("id"),it.getString("name"),it.optString("epgId"),it.optString("providerId")+":"+it.optString("category"),it.optString("categoryName","Other channels")) } } }
    suspend fun programmeBatch(channels:List<Channel>, from:Long, to:Long):Map<String,List<Programme>> {
        if(channels.isEmpty())return emptyMap()
        val data=JSONObject(api("/guide?channelIds="+Uri.encode(channels.joinToString(","){it.id})+"&from="+Uri.encode(java.time.Instant.ofEpochMilli(from).toString())+"&to="+Uri.encode(java.time.Instant.ofEpochMilli(to).toString()))).getJSONArray("programmes")
        val result=channels.associate{it.id to mutableListOf<Programme>()}
        for(i in 0 until data.length()){
            val p=data.getJSONObject(i)
            runCatching{Programme(p.getString("title"),java.time.Instant.parse(p.getString("start")).toEpochMilli(),java.time.Instant.parse(p.getString("end")).toEpochMilli())}.getOrNull()?.let{result[p.optString("channelId")]?.add(it)}
        }
        return result
    }
    private fun playback(j: JSONObject) = Playback(profile.base+j.getString("mediaUrl"),j.getString("id"),j.getInt("generation"),j.getString("state"),j.optString("reason"))
    override suspend fun play(channel: Channel)=playback(JSONObject(api("/playback/sessions","POST",JSONObject().put("channelId",channel.id).put("profile",profile.streamProfile))))
    override suspend fun heartbeat(id: String)=playback(JSONObject(api("/playback/sessions/$id/heartbeat","POST",JSONObject())))
    override suspend fun stop(id: String) { api("/playback/sessions/$id","DELETE") }
    override suspend fun programmes(channel: Channel): List<Programme> { val data=JSONObject(api("/guide?channelId="+Uri.encode(channel.id))).getJSONArray("programmes");return (0 until data.length()).mapNotNull { data.getJSONObject(it).let { p -> runCatching { Programme(p.getString("title"),java.time.Instant.parse(p.getString("start")).toEpochMilli(),java.time.Instant.parse(p.getString("end")).toEpochMilli()) }.getOrNull() } } }

}
class XtreamRepository(override val profile: Profile): Repository {
    private val epgSlots=Semaphore(3)
    override var connectionLimit: Int = 1
        private set
    private fun api(action: String) = Uri.parse(profile.base+"/player_api.php").buildUpon().appendQueryParameter("username",profile.username).appendQueryParameter("password",profile.password).apply { if(action.isNotEmpty())appendQueryParameter("action",action) }
    override suspend fun channels(): List<Channel> {
        val account=JSONObject(request(api("").build().toString())).optJSONObject("user_info")
        require(account?.optInt("auth")==1) { "Provider rejected the login" }
        require(account.optString("status","Active").equals("Active",true)) { "Provider account is not active" }
        connectionLimit=account.optInt("max_connections",1).takeIf{it>0}?:1
        val categories=runCatching { JSONArray(request(api("get_live_categories").build().toString())) }.getOrDefault(JSONArray())
        val names=(0 until categories.length()).associate { categories.getJSONObject(it).let { c -> c.optString("category_id") to c.optString("category_name","Other channels") } }
        val a=JSONArray(request(api("get_live_streams").build().toString()))
        return (0 until a.length()).map { a.getJSONObject(it).let { Channel(it.getString("stream_id"),it.optString("name","Channel"),it.optString("epg_channel_id"),it.optString("category_id","other"),names[it.optString("category_id")]?:"Other channels") } }.distinctBy { it.id }
    }
    override suspend fun play(channel: Channel)=Playback(profile.base+"/live/"+Uri.encode(profile.username)+"/"+Uri.encode(profile.password)+"/"+Uri.encode(channel.id)+"."+profile.format)
    override suspend fun heartbeat(id: String)=error("Standalone playback has no server session")
    override suspend fun stop(id: String) {}
    override suspend fun programmes(channel: Channel): List<Programme> = epgSlots.withPermit {
        val a=JSONObject(request(api("get_short_epg").appendQueryParameter("stream_id",channel.id).appendQueryParameter("limit","30").build().toString())).optJSONArray("epg_listings")?:return@withPermit emptyList()
        (0 until a.length()).mapNotNull { a.getJSONObject(it).let { p ->
            val title=p.optString("title");val decoded=try{String(android.util.Base64.decode(title,android.util.Base64.DEFAULT),Charsets.UTF_8)}catch(_:Exception){title}
            val start=p.optLong("start_timestamp",0)*1000;val end=p.optLong("stop_timestamp",p.optLong("end_timestamp",0))*1000
            if(start>0 && end>start)Programme(decoded,start,end,decodeEpgText(p.optString("description")))else null
        } }.sortedBy { it.start }
    }
}

private fun decodeEpgText(value:String)=try{String(android.util.Base64.decode(value,android.util.Base64.DEFAULT),Charsets.UTF_8)}catch(_:Exception){value}
data class Programme(val title: String, val start: Long, val end: Long, val description:String="")
