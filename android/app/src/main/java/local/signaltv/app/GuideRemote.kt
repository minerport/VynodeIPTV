package local.signaltv.app

/** One press causes exactly one action. Release after a long press never tunes. */
class GuideSelectPress {
    enum class Action { NONE, WATCH, MENU }
    private var held=false
    private var opened=false
    fun down(repeat:Int,elapsed:Long):Action {
        if(repeat==0){held=true;opened=false}
        if(held&&!opened&&elapsed>=500){opened=true;return Action.MENU}
        return Action.NONE
    }
    fun up(elapsed:Long,cancelled:Boolean=false):Action {
        val action=if(!held||opened||cancelled)Action.NONE else if(elapsed>=500)Action.MENU else Action.WATCH
        reset();return action
    }
    fun reset(){held=false;opened=false}
}

fun safeChannelLogo(raw:String):String = runCatching {
    val value=raw.trim();val u=java.net.URI(value)
    if(value.length<=2048&&u.scheme=="https"&&!u.host.isNullOrBlank()&&u.rawUserInfo==null&&u.rawQuery==null&&u.rawFragment==null)value else ""
}.getOrDefault("")
