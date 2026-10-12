package local.signaltv.app

/** Wall intent is explicit; a single saved channel does not imply a wall. */
data class WallMode(val enabled:Boolean=false){
    fun enter()=WallMode(true)
    fun exit()=WallMode(false)
    companion object {
        fun restore(saved:Boolean?,occupied:Int)=WallMode(occupied>0 && (saved ?: (occupied>1)))
    }
}
