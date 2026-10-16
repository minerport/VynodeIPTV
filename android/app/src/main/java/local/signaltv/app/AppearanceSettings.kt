package local.signaltv.app

data class AppearanceSettings(
 val layout:String="classic",val palette:String="cinema",val surface:String="matte",val font:String="standard",
 val scale:Float=1f,val density:String="compact",val corners:String="soft",val borders:String="clear",val focus:String="outline",val artwork:String="all"
){
 fun normalized()=copy(
  layout=layout.takeIf{it in listOf("classic","guide","cinema")}?:"classic",
  palette=palette.takeIf{it in listOf("cinema","obsidian","arctic","midnight","ember","pearl","contrast")}?:"cinema",
  surface=surface.takeIf{it in listOf("matte","frosted","smoked")}?:"matte",
  font=font.takeIf{it in listOf("standard","modern","readable")}?:"standard",
  scale=scale.takeIf{it in listOf(.9f,1f,1.15f,1.3f)}?:1f,
  density=density.takeIf{it in listOf("compact","comfortable","large")}?:"compact",
  corners=corners.takeIf{it in listOf("square","soft","rounded")}?:"soft",
  borders=borders.takeIf{it in listOf("clear","strong")}?:"clear",
  focus=focus.takeIf{it in listOf("outline","fill","glow")}?:"outline",
  artwork=artwork.takeIf{it in listOf("all","header","off")}?:"all")
 val radius:Int get()=when(corners){"square"->0;"rounded"->14;else->7}
 val guideDensity:GuideDensity get()=when(density){"comfortable"->GuideDensity.COMFORTABLE;"large"->GuideDensity.LARGE;else->GuideDensity.COMPACT}
}
