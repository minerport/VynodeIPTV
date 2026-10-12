package local.signaltv.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class CustomGroup(val id: String, val name: String, val channelIds: Set<String>) {
    fun json()=JSONObject().put("name",name).put("channelIds",JSONArray(channelIds.toList()))
}
class Groups(context: Context, private val profile: Profile) {
    private val prefs=context.getSharedPreferences("channel-groups",Context.MODE_PRIVATE)
    private val key=MessageDigest.getInstance("SHA-256").digest((profile.mode+"|"+profile.base+"|"+profile.username).toByteArray()).joinToString(""){"%02x".format(it)}
    suspend fun load(): List<CustomGroup> {
        val a=JSONArray(if(profile.mode=="server")request(profile.base+"/api/v1/groups",profile.token)else prefs.getString(key,"[]"))
        return (0 until a.length()).map { a.getJSONObject(it).let { g -> val ids=g.getJSONArray("channelIds");CustomGroup(g.getString("id"),g.getString("name"),(0 until ids.length()).map { ids.getString(it) }.toSet()) } }
    }
    suspend fun save(group: CustomGroup) {
        require(group.name.isNotBlank() && group.name.length<=80 && group.channelIds.size<=500){"Use a name up to 80 characters and at most 500 channels"}
        if(profile.mode=="server")request(profile.base+"/api/v1/groups"+if(group.id.isEmpty())"" else "/"+group.id,profile.token,if(group.id.isEmpty())"POST" else "PUT",group.json())
        else { val saved=group.copy(id=group.id.ifEmpty { java.util.UUID.randomUUID().toString() });val all=load().filter { it.id!=saved.id }+saved;require(all.size<=50){"Maximum 50 groups"};persist(all) }
    }
    suspend fun delete(id: String) { if(profile.mode=="server")request(profile.base+"/api/v1/groups/"+id,profile.token,"DELETE")else persist(load().filter { it.id!=id }) }
    private fun persist(groups: List<CustomGroup>) { check(prefs.edit().putString(key,JSONArray(groups.map { it.json().put("id",it.id) }).toString()).commit()){ "Could not save groups" } }
    suspend fun preferences(): GuidePreferences {
        val json=JSONObject(if(profile.mode=="server")request(profile.base+"/api/v1/guide/preferences",profile.token)else prefs.getString(key+"-preferences","{}")?:"{}")
        fun values(name:String):List<String>{val a=json.optJSONArray(name)?:JSONArray();return (0 until a.length()).map{a.getString(it)}}
        return GuidePreferences(values("favorites").toSet(),values("groupOrder"),values("hiddenGroups").toSet())
    }
    suspend fun savePreferences(value: GuidePreferences) {
        require(value.favorites.size<=500){"Maximum 500 favorites"}
        val json=JSONObject().put("favorites",JSONArray(value.favorites.toList())).put("groupOrder",JSONArray(value.groupOrder)).put("hiddenGroups",JSONArray(value.hiddenGroups.toList()))
        if(profile.mode=="server")request(profile.base+"/api/v1/guide/preferences",profile.token,"PUT",json)
        else check(prefs.edit().putString(key+"-preferences",json.toString()).commit()){"Could not save preferences"}
    }
}

data class GuidePreferences(val favorites:Set<String> = emptySet(),val groupOrder:List<String> = emptyList(),val hiddenGroups:Set<String> = emptySet())
