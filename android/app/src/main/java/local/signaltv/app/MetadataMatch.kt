package local.signaltv.app

data class MetadataQuery(val title:String,val year:String="")
data class MetadataCandidate(val id:Int,val kind:String,val title:String,val original:String="",val date:String="",val adult:Boolean=false)
fun metadataTitle(value:String)=value.lowercase(java.util.Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()
fun cleanMetadataQuery(title:String,year:String=""):MetadataQuery {
 val match=Regex("\\s*\\((19[0-9]{2}|20[0-9]{2})\\)\\s*$").find(title)
 return MetadataQuery(if(match==null)title.trim() else title.substring(0,match.range.first).trim(),year.takeIf{Regex("(19|20)[0-9]{2}").matches(it)}?:match?.groupValues?.get(1).orEmpty())
}
fun metadataAllowed(q:MetadataQuery):Boolean {
 val title=metadataTitle(q.title);if(title.length<4||q.title.length>240)return false
 return listOf("news","football","basketball","baseball","hockey","soccer","tennis","golf","nfl","nba","mlb","nhl","paid programming","untitled programme","no epg data","to be announced").none{(" $title ").contains(" $it ")}
}
fun chooseMetadata(q:MetadataQuery,results:List<MetadataCandidate>,pages:Int):MetadataCandidate? {
 if(pages>1||!metadataAllowed(q))return null
 val title=metadataTitle(q.title)
 return results.filter{!it.adult&&it.id>0&&it.kind in listOf("movie","tv")&&(metadataTitle(it.title)==title||metadataTitle(it.original)==title)&&(q.year.isBlank()||it.date.startsWith(q.year+"-"))}.singleOrNull()
}
fun safeMetadataArtwork(value:String)=Regex("https://image\\.tmdb\\.org/t/p/(w342|w780)/[A-Za-z0-9_-]+\\.(jpg|png)").matches(value)
