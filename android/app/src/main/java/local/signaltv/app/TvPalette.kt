package local.signaltv.app

/** Semantic colors shared with the browser appearance catalog. */
object TvPalette {
 val Background get()=Appearance.color("background")
 val Glow get()=Appearance.color("programme")
 val Surface get()=Appearance.color("surface")
 val Track get()=Appearance.color("background")
 val Programme get()=Appearance.color("programme")
 val Live get()=Appearance.color("live")
 val Accent get()=Appearance.color("accent")
 val Text get()=Appearance.color("text")
 val Muted get()=Appearance.color("muted")
 val OnAccent get()=Appearance.color("onAccent")
 val Signal get()=Appearance.color("accent")
 val GuideBorder get()=Appearance.color("border")
 val Line get()=Appearance.color("border").copy(alpha=.6f)
}
