package local.signaltv.app

data class Programme(val title:String,val start:Long,val end:Long,val description:String="",val year:String="",val season:Int=0,val episode:Int=0,val episodeTitle:String="",val genres:String="")
data class GuideCell(val start:Long,val end:Long,val programme:Programme?)

suspend fun providerEpg(now:Long,fetch:suspend (String)->List<Programme>):List<Programme>{
    val full=try{fetch("get_simple_data_table")}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){emptyList()}
    val next=if(full.none{it.start<=now && it.end>now})try{fetch("get_short_epg")}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){if(full.isEmpty())throw e else emptyList()}else emptyList()
    return (full+next).filter{it.end>it.start && it.end>now-7*86400000 && it.start<now+7*86400000}.distinctBy{it.start to it.end}.sortedBy{it.start}
}

/** Cover the entire visible window, including provider gaps, without overlapping cells. */
fun guideCells(programmes:List<Programme>,start:Long,end:Long):List<GuideCell>{
    if(end<=start)return emptyList()
    val result=mutableListOf<GuideCell>();var cursor=start
    for(p in programmes.filter{it.end>it.start && it.end>start && it.start<end}.sortedWith(compareBy<Programme>{it.start}.thenBy{it.end})){
        val left=maxOf(cursor,p.start);val right=minOf(end,p.end)
        if(right<=left)continue
        if(left>cursor)result.add(GuideCell(cursor,left,null))
        result.add(GuideCell(left,right,p));cursor=right
    }
    if(cursor<end)result.add(GuideCell(cursor,end,null))
    return result
}

fun epgTime(timestamp:String,date:String,zone:java.time.ZoneId):Long{
    timestamp.toLongOrNull()?.takeIf{it>0}?.let{return if(it>100000000000L)it else it*1000}
    runCatching{java.time.Instant.parse(date).toEpochMilli()}.getOrNull()?.let{return it}
    return runCatching{java.time.LocalDateTime.parse(date.replace(' ','T')).atZone(zone).toInstant().toEpochMilli()}.getOrDefault(0)
}

fun decodeEpgText(value:String):String {
    if(value.isBlank())return ""
    return runCatching{
        val bytes=java.util.Base64.getDecoder().decode(value)
        val text=Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        if(text.any{it.isISOControl() && it!='\n' && it!='\r' && it!='\t'})value else text
    }.getOrDefault(value)
}

/** Live playback metadata must never use the first historical or future listing. */
fun currentProgramme(programmes:List<Programme>,now:Long):Programme? =
    programmes.filter{it.start<=now && it.end>now}.maxByOrNull{it.start}

fun programmeEpoch(value:String):Long=java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()
