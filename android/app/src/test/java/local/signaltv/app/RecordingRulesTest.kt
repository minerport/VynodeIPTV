package local.signaltv.app

import org.junit.Assert.*
import org.junit.Test

class RecordingRulesTest {
 @Test fun adjacentSchedulesDoNotConflict(){assertFalse(recordingConflict(listOf(RecordingWindow(0,10)),RecordingWindow(10,20),1))}
 @Test fun overlapHonorsProviderBudget(){assertTrue(recordingConflict(listOf(RecordingWindow(0,11),RecordingWindow(10,20)),RecordingWindow(0,20),2));assertFalse(recordingConflict(listOf(RecordingWindow(0,10),RecordingWindow(10,20)),RecordingWindow(0,20),2))}
 @Test fun recordingAndLiveShareBudget(){try{assertTrue(ProviderSlots.acquire("record-test","provider-test",1));assertFalse(ProviderSlots.acquire("live-test","provider-test",1));assertTrue(ProviderSlots.acquire("other-test","other-provider",1));ProviderSlots.release("record-test");assertTrue(ProviderSlots.acquire("live-test","provider-test",1))}finally{listOf("record-test","live-test","other-test").forEach(ProviderSlots::release)}}
}
