package local.signaltv.app

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.*
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicLong

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackEngine(private val context: Context, private val repo: Repository, private val report: (String)->Unit) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val born=SystemClock.elapsedRealtime()
    private val bytes=AtomicLong()
    private val frames=AtomicLong()
    private val headers=if(repo.profile.mode=="server")mapOf("Authorization" to "Bearer ${repo.profile.token}")else emptyMap()
    private val transfers=object:TransferListener{
        override fun onTransferInitializing(source:DataSource,dataSpec:DataSpec,isNetwork:Boolean){}
        override fun onTransferStart(source:DataSource,dataSpec:DataSpec,isNetwork:Boolean){}
        override fun onTransferEnd(source:DataSource,dataSpec:DataSpec,isNetwork:Boolean){}
        override fun onBytesTransferred(source:DataSource,dataSpec:DataSpec,isNetwork:Boolean,bytesTransferred:Int){if(isNetwork)bytes.addAndGet(bytesTransferred.toLong())}
    }
    private fun buildPlayer():ExoPlayer{
        val http=DefaultHttpDataSource.Factory().setConnectTimeoutMs(10000).setReadTimeoutMs(12000).setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(repo.profile.mode=="direct" && repo.profile.allowHttp).setUserAgent("SignalTV/0.5").setTransferListener(transfers)
        return ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http).setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(2)))
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(15000,30000,2000,4000).build()).build()
    }
    var player by mutableStateOf(buildPlayer())
        private set
    private var audible:Boolean?=null
    private var visibleSurfaces=0
    private var surfaceChangedAt=born
    private var audioChangedAt=born
    private var underruns=0
    private var audioDecoderActive=false
    private var startupMs:Long?=null
    private val monitor=StreamHealth()
    private val events=HealthEvents()
    private var totalRecoveries=0
    var diagnostics by mutableStateOf("Starting playback")
        private set
    private var playback: Playback?=null
    private var loadedGeneration=-1
    private var retry: Job?=null
    private val budget=RecoveryBudget()
    private var closed=false
    private var previousCondition:StreamCondition?=null
    private fun event(message:String){events.add((SystemClock.elapsedRealtime()-born)/1000,message)}
    fun surfaceAttached(){visibleSurfaces++;surfaceChangedAt=SystemClock.elapsedRealtime()}
    fun surfaceDetached(){visibleSurfaces=(visibleSurfaces-1).coerceAtLeast(0);surfaceChangedAt=SystemClock.elapsedRealtime()}
    fun setAudible(enabled:Boolean){
        if(audible==enabled)return
        if(!enabled)player.volume=0f
        player.trackSelectionParameters=player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,!enabled).build()
        if(enabled)player.volume=1f
        audible=enabled;audioChangedAt=SystemClock.elapsedRealtime()
    }
    init { installListeners();event("Player created") }
    private fun installListeners(){
        player.setVideoFrameMetadataListener{_,_,_,_->frames.incrementAndGet()}
        player.addAnalyticsListener(object:AnalyticsListener{
            override fun onAudioDecoderInitialized(eventTime:AnalyticsListener.EventTime,decoderName:String,initializedTimestampMs:Long,initializationDurationMs:Long){audioDecoderActive=true;audioChangedAt=SystemClock.elapsedRealtime()}
            override fun onAudioDecoderReleased(eventTime:AnalyticsListener.EventTime,decoderName:String){audioDecoderActive=false}
            override fun onAudioDisabled(eventTime:AnalyticsListener.EventTime,decoderCounters:androidx.media3.exoplayer.DecoderCounters){audioDecoderActive=false}
            override fun onAudioUnderrun(eventTime:AnalyticsListener.EventTime,bufferSize:Int,bufferSizeMs:Long,elapsedSinceLastFeedMs:Long){underruns++}
        })
        player.addListener(object: Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val old=playback
                if(repo.profile.mode=="direct" && repo.profile.allowHttp && repo.profile.httpFallback && transportFailure(error) && old!=null && java.net.URI(old.url).host==java.net.URI(repo.profile.base).host){
                    fallbackAddress(old.url)?.let{url->retry?.cancel();playback=old.copy(url=url);event("Using opted-in HTTP fallback");report("Retrying provider over HTTP");prepare(url);return}
                }

                val cause=generateSequence(error as Throwable?) { it.cause }.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
                when{
                    cause?.responseCode in listOf(401,403,404)->{retry?.cancel();player.pause();event("Access failure HTTP ${cause?.responseCode}");report("Provider returned HTTP ${cause?.responseCode}. Check subscription and channel access.")}
                    (cause?.responseCode?:0) in 300..399 && !repo.profile.allowHttp->{retry?.cancel();player.pause();event("HTTP redirect blocked");report("Stream redirect blocked. Check Allow HTTP streams in connection settings.")}
                    else->recover("Player error: ${error.errorCodeName}")
                }
            }
            override fun onPlaybackStateChanged(state: Int) { if(state==Player.STATE_ENDED)recover("Live stream ended") }
            override fun onIsPlayingChanged(isPlaying: Boolean) { if(isPlaying){if(startupMs==null){startupMs=SystemClock.elapsedRealtime()-born;event("First playback after ${startupMs}ms")};report("Playing")} }
        })
    }
    fun start(channel: Channel) { scope.launch {
        try {
            report("Opening ${channel.name}")
            val opened=withContext(NonCancellable){repo.play(channel)}
            if(!isActive||closed){opened.sessionId?.let{withContext(NonCancellable){runCatching{repo.stop(it)}}};return@launch}
            playback=opened
            if(opened.sessionId==null)prepare(opened.url)
            while(isActive){delay(1000);checkHealth()}
        }catch(e: CancellationException){throw e}catch(_: Exception){event("Unable to open stream");diagnostics="Unable to open stream";report("Unable to open channel. Check the connection and provider stream limit.")}
    };if(repo.profile.mode=="server")scope.launch {
        var failures=0
        while(isActive){delay(3000);val old=playback?:continue;val id=old.sessionId?:continue
            try {
                val next=repo.heartbeat(id);failures=0
                if(next.state=="failed"){retry?.cancel();if(old.state!="failed")event("Server reports failed output");report(next.reason);player.pause();playback=next;continue}
                if(next.state=="playing" && (loadedGeneration!=next.generation||player.currentMediaItem==null)){
                    retry?.cancel();loadedGeneration=next.generation;event("Server output generation ${next.generation}");prepare(next.url)
                }
                if(next.state!="playing")report(next.reason)
                playback=next
            }catch(e: CancellationException){throw e}catch(_: Exception){failures++;event("Server heartbeat failed ($failures/5)");report("Server connection interrupted · waiting");if(failures>=5){retry?.cancel();player.pause();report("Server unavailable or access revoked. Reconnect this channel.");return@launch}}
        }
    } }
    private fun checkHealth(){
        val now=SystemClock.elapsedRealtime()
        val audio=player.audioDecoderCounters?.also{it.ensureUpdated()}?.renderedOutputBufferCount?.toLong()
        val video=player.videoDecoderCounters?.also{it.ensureUpdated()}
        val watchedVideo=if(visibleSurfaces>0 && player.videoFormat!=null && now-surfaceChangedAt>=2000)frames.get()else null
        // Passthrough and absent counters cannot establish audible output; report unavailable instead.
        val watchedAudio=if(audible==true && audioDecoderActive && now-audioChangedAt>=2000 && audio!=null && audio>0)audio else null
        val sample=StreamSample(now,player.playWhenReady && player.playbackSuppressionReason==Player.PLAYBACK_SUPPRESSION_REASON_NONE && playback?.state=="playing",
            player.playbackState==Player.STATE_BUFFERING,player.currentPosition,player.totalBufferedDuration.coerceAtLeast(0),bytes.get(),watchedVideo,watchedAudio)
        val condition=monitor.sample(sample)
        if(condition!=previousCondition){event(condition.label);previousCondition=condition}
        when{
            condition.stalled->{budget.stalled();recover(condition.label)}
            condition==StreamCondition.HEALTHY->{budget.healthy(now)}
            else->budget.stalled()
        }
        diagnostics=buildString{
            appendLine("${condition.label} · buffer ${sample.bufferedMs/1000}s")
            appendLine("Received ${sample.bytes/1024} KiB · video frames ${frames.get()} · dropped ${video?.droppedBufferCount?:0}")
            appendLine("Audio decoder buffers: ${watchedAudio?:"unavailable / muted"} · underruns $underruns")
            appendLine("First playback: ${startupMs?.let{"${it}ms"}?:"waiting"} · recoveries $totalRecoveries · budget ${budget.attempts}/5")
            if(repo.profile.mode=="server")appendLine("Server: ${playback?.state} · generation $loadedGeneration")
            append(events.snapshot().takeLast(8).joinToString("\n"))
        }
    }
    private fun prepare(url: String){if(closed)return;monitor.reset();player.setMediaItem(MediaItem.fromUri(url));player.prepare();player.playWhenReady=true}
    private fun recover(reason: String){
        if(closed||retry?.isActive==true||!player.playWhenReady)return
        val wait=budget.nextDelay()
        if(wait==null){event("Recovery budget exhausted");report("Recovery limit reached. Remove and reopen this channel.");player.pause();return}
        val step=recoveryStep(budget.attempts,player.playerError==null && player.isCurrentMediaItemLive && player.isCurrentMediaItemSeekable)
        retry=scope.launch{
            event("$reason · ${step.label} scheduled (${budget.attempts}/5)");report("$reason · ${step.label} (${budget.attempts}/5)");delay(wait)
            if(closed || !player.playWhenReady)return@launch
            val current=playback?:return@launch
            if(current.sessionId!=null && current.state!="playing")return@launch
            // Let a transient stall recover by itself while the backoff is running.
            if(previousCondition==StreamCondition.HEALTHY && player.playerError==null && player.playbackState==Player.STATE_READY){event("Recovery skipped: progress resumed");return@launch}
            totalRecoveries++;event(step.label)
            try{when(step){
                RecoveryStep.LIVE_EDGE->{monitor.reset();player.seekToDefaultPosition();player.prepare()}
                RecoveryStep.RECONNECT->{player.stop();prepare(current.url)}
                RecoveryStep.REBUILD->{
                    val keepAudible=audible==true
                    val replacement=buildPlayer();player.release();frames.set(0);audioDecoderActive=false;player=replacement;audible=null;installListeners();setAudible(keepAudible);prepare(current.url)
                }
            }}catch(e:CancellationException){throw e}catch(_:Exception){event("Recovery operation failed");player.pause();report("Recovery failed. Remove and reopen this channel.")}
        }
    }
    private var closing: Job?=null
    fun close(): Job {
        closing?.let{return it}
        closed=true;val id=playback?.sessionId;scope.cancel();player.release()
        return CoroutineScope(Dispatchers.IO).launch{
            scope.coroutineContext[Job]?.join()
            if(id!=null)runCatching{repo.stop(id)}
        }.also{closing=it}
    }
}
