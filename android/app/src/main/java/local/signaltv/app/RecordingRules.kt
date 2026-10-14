package local.signaltv.app

data class RecordingWindow(val start:Long,val end:Long)

fun recordingConflict(windows:List<RecordingWindow>,next:RecordingWindow,limit:Int):Boolean {
 val events=mutableListOf(next.start to 1,next.end to -1)
 windows.filter{it.start<next.end&&it.end>next.start}.forEach{events+=maxOf(it.start,next.start) to 1;events+=minOf(it.end,next.end) to -1}
 var active=0
 return events.sortedWith(compareBy<Pair<Long,Int>>{it.first}.thenBy{it.second}).any{active+=it.second;active>limit.coerceAtLeast(1)}
}

object ProviderSlots {
 private val slots=mutableMapOf<String,String>()
 @Synchronized fun acquire(id:String,account:String,limit:Int):Boolean {if(id in slots)return true;if(slots.values.count{it==account}>=limit.coerceAtLeast(1))return false;slots[id]=account;return true}
 @Synchronized fun release(id:String){slots.remove(id)}
 @Synchronized fun contains(id:String)=id in slots
}
