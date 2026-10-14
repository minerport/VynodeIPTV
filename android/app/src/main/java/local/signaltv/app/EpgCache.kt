package local.signaltv.app

import android.content.Context
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.json.JSONArray
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EpgCache(context:Context,profile:Profile){
 private val file=File(context.filesDir,"epg-${profile.accountKey()}.json")
 private var stamp=-1L
 private var entries=emptyMap<String,List<Programme>>()
 @Synchronized fun load():Map<String,List<Programme>>{
  if(file.lastModified()==stamp)return entries
  val next=runCatching{val a=JSONArray(file.readText());val result=mutableMapOf<String,MutableList<Programme>>();for(i in 0 until a.length()){val r=a.getJSONArray(i);result.getOrPut(r.getString(0)){mutableListOf()}.add(Programme(r.getString(1),r.getLong(2),r.getLong(3),r.optString(4)))};result}.getOrDefault(emptyMap())
  entries=next;stamp=file.lastModified();return entries
 }
 @Synchronized fun save(values:Map<String,List<Programme>>){val a=JSONArray();values.forEach{(id,ps)->ps.forEach{p->a.put(JSONArray(listOf(id,p.title,p.start,p.end,p.description)))}};writeMetadata(file,a.toString());entries=values;stamp=file.lastModified()}
}
fun xmltvTime(value:String):Long = runCatching{val clean=value.trim();OffsetDateTime.parse(if(clean.length==14)clean+" +0000" else clean,DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")).toInstant().toEpochMilli()}.getOrDefault(0)
suspend fun importEpg(profile:Profile,channels:List<Channel>):Map<String,List<Programme>> = withContext(Dispatchers.IO){
 val url=android.net.Uri.parse(profile.base+"/xmltv.php").buildUpon().appendQueryParameter("username",profile.username).appendQueryParameter("password",profile.password).build().toString()
 val xml=request(url,httpFallback=profile.httpFallback&&profile.allowHttp);val parser=Xml.newPullParser();parser.setInput(xml.reader())
 val ids=channels.filter{it.epgId.isNotBlank()}.groupBy{it.epgId};val result=mutableMapOf<String,MutableList<Programme>>();val now=System.currentTimeMillis()
 var channel="";var start=0L;var end=0L;var title="";var description="";var count=0
 var rootSeen=false
 while(parser.eventType!=XmlPullParser.END_DOCUMENT){
  if(parser.eventType==XmlPullParser.START_TAG && !rootSeen){check(parser.name=="tv"){"Provider did not return an XMLTV guide"};rootSeen=true}
  if(parser.eventType==XmlPullParser.START_TAG)when(parser.name){
   "programme"->{channel=parser.getAttributeValue(null,"channel").orEmpty();start=xmltvTime(parser.getAttributeValue(null,"start").orEmpty());end=xmltvTime(parser.getAttributeValue(null,"stop").orEmpty());title="";description=""}
   "title"->title=parser.nextText()
   "desc"->description=parser.nextText()
  }
  if(parser.eventType==XmlPullParser.END_TAG && parser.name=="programme" && end>start && end>now-7*86400000L && start<now+7*86400000L){ids[channel]?.forEach{c->check(++count<=300000){"EPG exceeds device import limit"};result.getOrPut(c.id){mutableListOf()}.add(Programme(title.ifBlank{"Untitled programme"},start,end,description))}}
  parser.next()
 }
 check(rootSeen){"Provider returned an empty guide response"}
 check(result.isNotEmpty()){"No matching programmes; previous guide retained"}
 result.mapValues{it.value.sortedBy{p->p.start}}
}
