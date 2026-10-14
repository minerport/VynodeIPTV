package local.signaltv.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** App-private metadata only; provider passwords and tokens are never serialized. */
class CatalogCache(context:Context,profile:Profile){
    private val identity=listOf(profile.mode,profile.base,profile.username,profile.token).joinToString("\u0000")
    private val key=MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString(""){"%02x".format(it)}
    private val file=File(context.filesDir,"catalog-$key.json")
    private val legacy=File(context.cacheDir,"catalog-$key.json")
    fun modified():Long=file.lastModified()
    fun load():List<Channel> = runCatching{
        val source=if(file.exists())file else legacy
        if(!source.exists() || source.length()>32*1024*1024)return emptyList()
        val a=JSONArray(source.readText())
        (0 until a.length()).map{a.getJSONObject(it).let{j->Channel(j.getString("id"),j.getString("name"),j.optString("epgId"),j.getString("groupId"),j.getString("groupName"))}}
    }.getOrDefault(emptyList())
    fun save(channels:List<Channel>){
        val a=JSONArray();channels.forEach{a.put(JSONObject().put("id",it.id).put("name",it.name).put("epgId",it.epgId).put("groupId",it.groupId).put("groupName",it.groupName))}
        writeMetadata(file,a.toString())
    }
}

internal fun writeMetadata(file:File,text:String){
    val temporary=File.createTempFile(file.name,".tmp",file.parentFile)
    try{temporary.writeText(text);check(temporary.renameTo(file)){"Could not save guide data"}}finally{temporary.delete()}
}
