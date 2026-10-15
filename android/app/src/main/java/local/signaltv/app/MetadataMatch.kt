package local.signaltv.app

data class MetadataQuery(val title:String,val year:String="",val season:Int=0,val episode:Int=0,val episodeTitle:String="")
data class MetadataCandidate(val id:Int,val kind:String,val title:String,val original:String="",val date:String="",val adult:Boolean=false)
fun metadataTitle(value:String)=value.lowercase(java.util.Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()
fun cleanMetadataQuery(title:String,year:String="",season:Int=0,episode:Int=0,episodeTitle:String=""):MetadataQuery {
 var name=title.trim();var sn=season;var en=episode
 if(sn !in 0..1000 || en !in 1..10000){sn=0;en=0}
 val marker=Regex("(?i)^(.+?)\\s+[-:–]?\\s*S([0-9]{1,3})\\s*E([0-9]{1,4})\\s*$").find(name)
 if(marker!=null){val a=marker.groupValues[2].toInt();val b=marker.groupValues[3].toInt();if(b>0&&(en==0||(sn==a&&en==b))){name=marker.groupValues[1].trim();sn=a;en=b}}
 val match=Regex("\\s*\\((19[0-9]{2}|20[0-9]{2})\\)\\s*$").find(name)
 return MetadataQuery(if(match==null)name else name.substring(0,match.range.first).trim(),year.takeIf{Regex("(19|20)[0-9]{2}").matches(it)}?:match?.groupValues?.get(1).orEmpty(),sn,en,episodeTitle.trim())
}
fun programmeQuery(p:Programme)=cleanMetadataQuery(p.title,p.year,p.season,p.episode,p.episodeTitle)
fun metadataKey(q:MetadataQuery)="v2|"+metadataTitle(q.title)+"|"+q.year+"|"+q.season+"|"+q.episode+"|"+metadataTitle(q.episodeTitle)
fun parseEpisodeNumber(system:String,value:String):Pair<Int,Int>? {
 val m=when(system){"xmltv_ns"->Regex("^\\s*([0-9]+)(?:/[0-9]+)?\\s*\\.\\s*([0-9]+)(?:/[0-9]+)?\\s*\\.").find(value);"onscreen"->Regex("(?i)^S([0-9]{1,3})\\s*E([0-9]{1,4})$").find(value.trim());else->null}?:return null
 val offset=if(system=="xmltv_ns")1 else 0
 val s=m.groupValues[1].toIntOrNull()?.toLong()?.plus(offset)?:return null;val e=m.groupValues[2].toIntOrNull()?.toLong()?.plus(offset)?:return null
 return if(s in 0..1000 && e in 1..10000)s.toInt() to e.toInt() else null
}
fun upcomingMetadata(programmes:List<Programme>,now:Long)=programmes.asSequence().filter{it.end>now&&it.start<now+24*3600000L}.sortedBy{it.start}.filter{metadataAllowed(programmeQuery(it))}.distinctBy{metadataKey(programmeQuery(it))}.take(300).toList()
fun metadataAllowed(q:MetadataQuery):Boolean {
 val title=metadataTitle(q.title);if(title.length<4||q.title.length>240)return false
 return listOf("news","football","basketball","baseball","hockey","soccer","tennis","golf","nfl","nba","mlb","nhl","paid programming","untitled programme","no epg data","to be announced").none{(" $title ").contains(" $it ")}
}
fun chooseMetadata(q:MetadataQuery,results:List<MetadataCandidate>,pages:Int):MetadataCandidate? {
 if(pages>1||!metadataAllowed(q))return null
 val title=metadataTitle(q.title)
 return results.filter{!it.adult&&it.id>0&&it.kind in listOf("movie","tv")&&(metadataTitle(it.title)==title||metadataTitle(it.original)==title)&&(q.episode==0||it.kind=="tv")&&(q.episode>0||q.year.isBlank()||it.date.startsWith(q.year+"-"))}.singleOrNull()
}
fun safeMetadataArtwork(value:String)=Regex("https://image\\.tmdb\\.org/t/p/(w342|w780)/[A-Za-z0-9_-]+\\.(jpg|png)").matches(value)

fun explicitEpisode(season:String?,episode:String?):Pair<Int,Int>? {val s=season?.toIntOrNull()?:return null;val e=episode?.toIntOrNull()?:return null;return if(s in 0..1000 && e in 1..10000)s to e else null}

fun isTmdbApiKey(value:String)=Regex("[a-fA-F0-9]{32}").matches(value)
