package local.signaltv.app

enum class StreamCondition(val label:String,val stalled:Boolean=false){
    PAUSED("Paused / waiting"),STARTING("Starting"),HEALTHY("Healthy"),WATCHING("Watching progress"),
    STARVED("No incoming data",true),BUFFERING("Sustained buffering",true),
    CLOCK_STALLED("Playback position stalled",true),VIDEO_STALLED("Video frame delivery stalled",true),
    AUDIO_STALLED("Audio decoder progress stalled",true)
}
data class StreamSample(val now:Long,val active:Boolean,val buffering:Boolean,val position:Long,
    val bufferedMs:Long,val bytes:Long,val videoFrames:Long?,val audioBuffers:Long?)

/** Monotonic counters only; missing/disabled outputs must be passed as null. */
class StreamHealth(private val startupGrace:Long=25000,private val stallWindow:Long=12000){
    private var previous:StreamSample?=null
    private var started=0L
    private var positionAt=0L
    private var bytesAt=0L
    private var videoAt=0L
    private var audioAt=0L
    private var progressed=false
    fun reset(){previous=null;progressed=false}
    fun sample(s:StreamSample):StreamCondition{
        val old=previous
        previous=s
        if(!s.active){reset();return StreamCondition.PAUSED}
        if(old==null){started=s.now;positionAt=s.now;bytesAt=s.now;videoAt=s.now;audioAt=s.now;return StreamCondition.STARTING}
        if(s.position!=old.position){positionAt=s.now;progressed=true}
        if(s.bytes!=old.bytes)bytesAt=s.now
        if(s.videoFrames==null || old.videoFrames==null || s.videoFrames!=old.videoFrames)videoAt=s.now
        if(s.audioBuffers==null || old.audioBuffers==null || s.audioBuffers!=old.audioBuffers)audioAt=s.now
        if(!progressed && s.now-started<startupGrace)return StreamCondition.STARTING
        if(s.now-positionAt>=stallWindow){
            if(s.now-bytesAt>=stallWindow && s.bufferedMs<1000)return StreamCondition.STARVED
            return if(s.buffering)StreamCondition.BUFFERING else StreamCondition.CLOCK_STALLED
        }
        if(s.videoFrames!=null && s.now-videoAt>=stallWindow)return StreamCondition.VIDEO_STALLED
        if(s.audioBuffers!=null && s.now-audioAt>=stallWindow)return StreamCondition.AUDIO_STALLED
        return if(!s.buffering && s.now-positionAt<3000 && (s.videoFrames==null || s.now-videoAt<3000) &&
            (s.audioBuffers==null || s.now-audioAt<3000))StreamCondition.HEALTHY else StreamCondition.WATCHING
    }
}

enum class RecoveryStep(val label:String){LIVE_EDGE("Return to live edge"),RECONNECT("Reconnect media"),REBUILD("Rebuild player")}
fun recoveryStep(attempt:Int,canSeekLive:Boolean)=when{
    attempt==1 && canSeekLive->RecoveryStep.LIVE_EDGE
    attempt<=2->RecoveryStep.RECONNECT
    else->RecoveryStep.REBUILD
}

/** Only controlled messages, never raw exceptions, URLs, headers or credentials. */
class HealthEvents(private val capacity:Int=32){
    private val entries=ArrayDeque<String>()
    fun add(seconds:Long,message:String){if(entries.size==capacity)entries.removeFirst();entries.addLast("${seconds}s · $message")}
    fun snapshot():List<String> = entries.toList()
}
