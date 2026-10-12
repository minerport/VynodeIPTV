package local.signaltv.app

import org.junit.Assert.*
import org.junit.Test

class StreamHealthTest {
    private fun s(t:Long,pos:Long=t,bytes:Long=t*100,video:Long?=t/40,audio:Long?=t/20,active:Boolean=true,buffering:Boolean=false,buffer:Long=5000)=
        StreamSample(t,active,buffering,pos,buffer,bytes,video,audio)
    @Test fun startupGetsGraceThenNoBytesIsDetected(){val h=StreamHealth();h.sample(s(0));assertEquals(StreamCondition.STARTING,h.sample(s(24000,0,0,0,0,buffering=true,buffer=0)));assertEquals(StreamCondition.STARVED,h.sample(s(25000,0,0,0,0,buffering=true,buffer=0)))}
    @Test fun clockCanAdvanceWhileVideoIsFrozen(){val h=StreamHealth();h.sample(s(0));h.sample(s(1000));assertEquals(StreamCondition.VIDEO_STALLED,h.sample(s(14000,video=25)))}
    @Test fun audioCanStallIndependently(){val h=StreamHealth();h.sample(s(0));h.sample(s(1000));assertEquals(StreamCondition.AUDIO_STALLED,h.sample(s(14000,audio=50)))}
    @Test fun hiddenVideoAndMutedAudioAreNotFailures(){val h=StreamHealth();h.sample(s(0));h.sample(s(1000));assertEquals(StreamCondition.HEALTHY,h.sample(s(40000,video=null,audio=null)))}
    @Test fun reattachedOutputGetsFreshGrace(){val h=StreamHealth();h.sample(s(0,video=null));h.sample(s(20000,video=null));assertEquals(StreamCondition.HEALTHY,h.sample(s(21000,video=0)));assertEquals(StreamCondition.VIDEO_STALLED,h.sample(s(34000,video=0)))}
    @Test fun pauseAndResumeDoNotTriggerRecovery(){val h=StreamHealth();h.sample(s(0));assertEquals(StreamCondition.PAUSED,h.sample(s(40000,active=false)));assertEquals(StreamCondition.STARTING,h.sample(s(80000,0,0,0,0)))}
    @Test fun bufferedHlsMayStopDownloadingWhilePlaybackRemainsHealthy(){val h=StreamHealth();h.sample(s(0));assertEquals(StreamCondition.HEALTHY,h.sample(s(20000,bytes=0)))}
    @Test fun bytesArrivingDoesNotHideSustainedBuffering(){val h=StreamHealth();h.sample(s(0));h.sample(s(1000));assertEquals(StreamCondition.BUFFERING,h.sample(s(14000,pos=1000,buffering=true)))}
    @Test fun counterResetAndIndependentPanelsAreSafe(){val a=StreamHealth();val b=StreamHealth();a.sample(s(0));b.sample(s(0));a.sample(s(1000));assertEquals(StreamCondition.HEALTHY,b.sample(s(14000)));assertEquals(StreamCondition.VIDEO_STALLED,a.sample(s(14000,video=25)));a.reset();assertEquals(StreamCondition.STARTING,a.sample(s(15000,0,0,0,0)))}
    @Test fun recoveryEscalatesAndAvoidsUnsupportedLiveSeeks(){assertEquals(RecoveryStep.LIVE_EDGE,recoveryStep(1,true));assertEquals(RecoveryStep.RECONNECT,recoveryStep(1,false));assertEquals(RecoveryStep.RECONNECT,recoveryStep(2,true));assertEquals(RecoveryStep.REBUILD,recoveryStep(3,true))}
    @Test fun eventHistoryIsBounded(){val events=HealthEvents(3);repeat(100){events.add(it.toLong(),"Reconnect")};assertEquals(listOf("97s · Reconnect","98s · Reconnect","99s · Reconnect"),events.snapshot())}
}
