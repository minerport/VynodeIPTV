package local.signaltv.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class SavedScene(val ids:List<String>,val layout:Int,val selected:Int,val wallEnabled:Boolean?)
class SceneStore(context:Context,profile:Profile){
    private val prefs=context.getSharedPreferences("multiview",Context.MODE_PRIVATE)
    private val key=MessageDigest.getInstance("SHA-256").digest(listOf(profile.mode,profile.base,profile.username,profile.token).joinToString("\u0000").toByteArray()).joinToString(""){"%02x".format(it)}
    fun save(slots:List<Channel?>,layout:Int,selected:Int,wallEnabled:Boolean){prefs.edit().putString(key,JSONObject().put("ids",JSONArray(slots.map{it?.id.orEmpty()})).put("layout",layout).put("selected",selected).put("wallEnabled",wallEnabled).toString()).apply()}
    fun load():SavedScene?=runCatching{
        val j=JSONObject(prefs.getString(key,null)?:return null);val a=j.getJSONArray("ids")
        SavedScene(List(6){a.optString(it)},j.optInt("layout",2).takeIf{it in listOf(2,4,6)}?:2,j.optInt("selected",0).coerceIn(0,5),if(j.has("wallEnabled"))j.optBoolean("wallEnabled")else null)
    }.getOrNull()
}
