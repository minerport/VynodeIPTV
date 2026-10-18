package local.signaltv.app

/** Dispatch on release so a newly opened dialog cannot receive the same key-up. */
class GuideSelectPress {
    enum class Action { NONE, WATCH, MENU }
    private var held=false
    private var opened=false
    fun down(repeat:Int,elapsed:Long):Action {
        if(repeat==0){held=true;opened=false}
        if(held&&!opened&&elapsed>=500){opened=true;return Action.NONE}
        return Action.NONE
    }
    fun up(elapsed:Long,cancelled:Boolean=false):Action {
        val action=if(!held||cancelled)Action.NONE else if(opened||elapsed>=500)Action.MENU else Action.WATCH
        reset();return action
    }
    fun reset(){held=false;opened=false}
}

fun safeChannelLogo(raw:String):String = runCatching {
    val value=raw.trim();val u=java.net.URI(value)
    if(value.length<=2048&&u.scheme=="https"&&!u.host.isNullOrBlank()&&u.rawUserInfo==null&&u.rawQuery==null&&u.rawFragment==null)value else ""
}.getOrDefault("")

fun guideStartAfterShift(start:Long,delta:Long,now:Long):Long = (start+delta).coerceIn(now,now+7*86400000L)

/** Keep live listings current without pulling a future browsing window backward. */
fun guideStartOnTick(start:Long,now:Long):Long = maxOf(start,now)
