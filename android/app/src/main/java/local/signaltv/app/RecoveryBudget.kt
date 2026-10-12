package local.signaltv.app

/** A tune owns one budget. Pauses and old generations must not consume it. */
class RecoveryBudget {
    var attempts=0; private set
    private var healthySince: Long?=null
    fun nextDelay(): Long? { healthySince=null;if(attempts>=5)return null;return (1L shl attempts++)*1000 }
    fun healthy(now: Long) { if(healthySince==null)healthySince=now;if(now-(healthySince?:now)>=60000)attempts=0 }
    fun stalled(){healthySince=null}
}
