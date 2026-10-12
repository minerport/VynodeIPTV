package local.signaltv.app
import org.junit.Assert.*
import org.junit.Test
class RecoveryBudgetTest {
    @Test fun stopsRetryStorms(){val b=RecoveryBudget();assertEquals(listOf(1000L,2000L,4000L,8000L,16000L),List(5){b.nextDelay()});assertNull(b.nextDelay())}
    @Test fun briefRecoveryDoesNotResetBudget(){val b=RecoveryBudget();b.nextDelay();b.healthy(0);b.healthy(10000);assertEquals(1,b.attempts);b.stalled();b.healthy(20000);b.healthy(80000);assertEquals(0,b.attempts)}
}
