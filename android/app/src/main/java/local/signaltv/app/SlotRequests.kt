package local.signaltv.app

/** Reject late work independently per panel, with a global lifecycle boundary. */
class SlotRequests(size:Int=6){
    data class Ticket(val slot:Int,val revision:Int,val epoch:Int)
    private val revisions=IntArray(size)
    var epoch=0
        private set
    fun begin(slot:Int)=Ticket(slot,++revisions[slot],epoch)
    fun remove(slot:Int){revisions[slot]++}
    fun suspendAll(){epoch++}
    fun accepts(ticket:Ticket)=ticket.epoch==epoch && ticket.revision==revisions[ticket.slot]
}
