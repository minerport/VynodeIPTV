package local.signaltv.app

import org.junit.Assert.*
import org.junit.Test

class SlotRequestsTest {
    @Test fun rapidReplacementCannotReopenOldChannel(){
        val requests=SlotRequests();val old=requests.begin(0);val replacement=requests.begin(0)
        assertFalse(requests.accepts(old));assertTrue(requests.accepts(replacement))
    }
    @Test fun openingAnotherPanelDoesNotCancelPendingFirstPanel(){
        val requests=SlotRequests();val first=requests.begin(0);val second=requests.begin(5)
        assertTrue(requests.accepts(first));assertTrue(requests.accepts(second))
        requests.remove(0)
        assertFalse(requests.accepts(first));assertTrue(requests.accepts(second))
    }
    @Test fun backgroundingInvalidatesEveryPendingOpenButAllowsFreshResume(){
        val requests=SlotRequests();val pending=(0..5).map{requests.begin(it)}
        requests.suspendAll();pending.forEach{assertFalse(requests.accepts(it))}
        assertTrue(requests.accepts(requests.begin(0)))
    }
}
