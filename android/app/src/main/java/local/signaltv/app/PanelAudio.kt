package local.signaltv.app

/** Mute the previous owner before enabling the new one, including late-starting panels. */
fun <T> routePanelAudio(panels:Map<Int,T>,selected:Int,setVolume:(T,Float)->Unit){
    panels.filterKeys{it!=selected}.values.forEach{setVolume(it,0f)}
    panels[selected]?.let{setVolume(it,1f)}
}
