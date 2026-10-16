package local.signaltv.app

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import org.json.JSONArray

object Appearance {
 var current by mutableStateOf(AppearanceSettings())
  private set
 private var loaded=false
 private var catalog=JSONObject()
 private lateinit var prefs:android.content.SharedPreferences
 fun load(context:Context){if(loaded)return;prefs=context.applicationContext.getSharedPreferences("appearance-studio",Context.MODE_PRIVATE);catalog=JSONObject(context.assets.open("appearance-presets.json").bufferedReader().use{it.readText()});current=decode(runCatching{JSONObject(prefs.getString("settings","{}")!!)}.getOrDefault(JSONObject()));loaded=true}
 fun migrateLegacy(context:Context,profile:Profile){load(context);if(prefs.contains("settings")||prefs.getBoolean("migrated",false))return;val old=GuideExperience(context,profile);current=current.copy(density=old.density().name.lowercase(),artwork=if(old.artwork())"all" else "header");prefs.edit().putBoolean("migrated",true).putString("settings",encode(current).toString()).apply()}
 fun preview(s:AppearanceSettings){current=s.normalized()}
 fun save(s:AppearanceSettings){val value=s.normalized();check(prefs.edit().putString("settings",encode(value).toString()).commit()){"Could not save appearance"};current=value}
 fun color(key:String,s:AppearanceSettings=current):Color {
  val fallback=when(key){"text"->"#f5f2eb";"muted"->"#b5b3af";"accent"->"#e6bc78";"onAccent"->"#17130d";"border"->"#72706b";"programme"->"#242424";"live"->"#383838";"surface"->"#1a1a1a";else->"#101010"}
  return Color(android.graphics.Color.parseColor(catalog.optJSONObject("palettes")?.optJSONObject(s.palette)?.optString(key,fallback)?:fallback))
 }
 fun presets():List<Pair<String,AppearanceSettings>>{val a=catalog.optJSONArray("presets")?:JSONArray();return (0 until a.length()).map{val p=a.getJSONObject(it);p.getString("name") to decode(p.getJSONObject("settings"))}}
 fun saved():List<Pair<String,AppearanceSettings>> = runCatching{val a=JSONArray(prefs.getString("presets","[]"));(0 until minOf(a.length(),20)).map{val p=a.getJSONObject(it);p.getString("name").take(40) to decode(p.getJSONObject("settings"))}}.getOrDefault(emptyList())
 fun savePreset(name:String,s:AppearanceSettings){require(name.trim().isNotEmpty()){ "Enter a preset name" };val list=saved().filter{it.first!=name.trim()}.toMutableList();require(list.size<20){"Maximum 20 presets"};list.add(name.trim().take(40) to s.normalized());writePresets(list)}
 fun deletePreset(name:String)=writePresets(saved().filter{it.first!=name})
 private fun writePresets(list:List<Pair<String,AppearanceSettings>>){val a=JSONArray();list.forEach{a.put(JSONObject().put("name",it.first).put("settings",encode(it.second)))};check(prefs.edit().putString("presets",a.toString()).commit()){"Could not save preset"}}
 fun encode(s:AppearanceSettings)=JSONObject().put("layout",s.layout).put("palette",s.palette).put("surface",s.surface).put("font",s.font).put("scale",kotlin.math.round(s.scale*100)/100.0).put("density",s.density).put("corners",s.corners).put("borders",s.borders).put("focus",s.focus).put("artwork",s.artwork)
 fun decode(j:JSONObject)=AppearanceSettings(j.optString("layout","classic"),j.optString("palette","cinema"),j.optString("surface","matte"),j.optString("font","standard"),j.optDouble("scale",1.0).toFloat(),j.optString("density","compact"),j.optString("corners","soft"),j.optString("borders","clear"),j.optString("focus","outline"),j.optString("artwork","all")).normalized()
 fun export(s:AppearanceSettings)=JSONObject().put("version",1).put("settings",encode(s.normalized())).toString(2)
 fun import(text:String):AppearanceSettings {require(text.length<=32768){"Preset is too large"};val j=JSONObject(text);require(j.optInt("version")==1&&j.optJSONObject("settings")!=null){"Unsupported appearance file"};return decode(j.getJSONObject("settings"))}
}
