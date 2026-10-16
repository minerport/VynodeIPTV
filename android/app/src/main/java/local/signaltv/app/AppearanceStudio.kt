package local.signaltv.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*

@Composable private fun AppearanceChoice(label:String,value:String,values:List<String>,change:(String)->Unit){
 var open by remember{mutableStateOf(false)}
 Box(Modifier.fillMaxWidth()){
  OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth()){Text("$label: ${value.replaceFirstChar{it.uppercase()}}",Modifier.weight(1f));Text("⌄")}
  DropdownMenu(expanded=open,onDismissRequest={open=false}){values.forEach{v->DropdownMenuItem(text={Text((if(v==value)"✓ " else "")+v.replaceFirstChar{it.uppercase()})},onClick={open=false;change(v)})}}
 }
}
@Composable private fun AppearancePreview(s:AppearanceSettings){
 val text=Appearance.color("text",s);val accent=Appearance.color("accent",s)
 Column(Modifier.fillMaxWidth().background(Appearance.color("background",s),RoundedCornerShape(s.radius.dp)).border(1.dp,Appearance.color("border",s),RoundedCornerShape(s.radius.dp)).padding(8.dp)){
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
   Box(Modifier.width(if(s.layout=="cinema")100.dp else if(s.layout=="guide")40.dp else 70.dp).height(if(s.layout=="cinema")42.dp else 30.dp).background(Appearance.color("live",s)),contentAlignment=Alignment.Center){Text("▶",color=accent)}
   Text("The evening feature",color=text,fontFamily=appearanceFont(s.font),fontSize=14.sp)
  }
  repeat(if(s.layout=="guide")3 else 2){i->Row(Modifier.fillMaxWidth().padding(top=2.dp).background(Appearance.color("programme",s)).border(if(s.borders=="strong")2.dp else 1.dp,if(i==0)accent else Appearance.color("border",s)).padding(horizontal=5.dp,vertical=2.dp)){
   Text(if(i==0)"Cinema" else "World News",Modifier.weight(1f),color=text,fontSize=(10*s.scale).sp,fontFamily=appearanceFont(s.font),maxLines=1)
   Text(if(i==0)"The evening feature" else "News tonight",Modifier.weight(3f),color=text,fontSize=(10*s.scale).sp,fontFamily=appearanceFont(s.font),maxLines=1)
  }}
 }
}
@Composable fun AppearanceStudio(dismiss:()->Unit){
 val context=LocalContext.current;val scope=rememberCoroutineScope()
 var draft by remember{mutableStateOf(Appearance.current)};var original by remember{mutableStateOf(Appearance.current)}
 var pending by remember{mutableStateOf(false)};var remaining by remember{mutableIntStateOf(15)}
 var name by remember{mutableStateOf("")};var error by remember{mutableStateOf("")};var revision by remember{mutableIntStateOf(0)}
 fun revert(){if(pending)Appearance.preview(original);pending=false}
 fun close(){revert();dismiss()}
 DisposableEffect(Unit){onDispose{if(pending)Appearance.preview(original)}}
 LaunchedEffect(pending){if(pending){remaining=15;while(remaining>0){delay(1000);remaining--};revert()}}
 val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)scope.launch{try{val text=Appearance.export(draft);withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(text)}?:kotlin.error("Cannot write preset")};error="Preset exported"}catch(e:Exception){error=e.message?:"Could not export"}}}
 val importLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{try{draft=withContext(Dispatchers.IO){val bytes=context.contentResolver.openInputStream(uri)?.use{it.readBytesLimited(32768)}?:kotlin.error("Cannot read preset");Appearance.import(bytes.toString(Charsets.UTF_8))};error="Preset loaded. Apply to use it."}catch(e:Exception){error=e.message?:"Could not import"}}}
 Dialog(onDismissRequest={close()},properties=DialogProperties(usePlatformDefaultWidth=false)){
  Surface(Modifier.fillMaxWidth(.85f).heightIn(max=(LocalConfiguration.current.screenHeightDp*.88f).dp),shape=RoundedCornerShape(12.dp)){
   Column(Modifier.appearancePanel().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
    Text(if(pending)"Keep this appearance?" else "Appearance Studio",style=MaterialTheme.typography.titleLarge)
    if(pending){Text("Reverting in $remaining seconds.");Row{Button(onClick={try{Appearance.save(draft);pending=false;dismiss()}catch(e:Exception){error=e.message?:"Could not save"}}){Text("Keep")};TextButton(onClick={revert()}){Text("Revert")}}}
    else{
     AppearancePreview(draft)
     LazyColumn(Modifier.weight(1f,false).heightIn(min=80.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
      item{Text("Complete presets",style=MaterialTheme.typography.titleSmall)}
      item{AppearanceChoice("Preset","choose",Appearance.presets().map{it.first}){label->draft=Appearance.presets().first{it.first==label}.second}}
      item{AppearanceChoice("Layout",draft.layout,listOf("classic","guide","cinema")){draft=draft.copy(layout=it)}}
      item{AppearanceChoice("Colors",draft.palette,listOf("cinema","obsidian","arctic","midnight","ember","pearl","contrast")){draft=draft.copy(palette=it)}}
      item{AppearanceChoice("Surface",draft.surface,listOf("matte","frosted","smoked")){draft=draft.copy(surface=it)}}
      item{AppearanceChoice("Font",draft.font,listOf("standard","modern","readable")){draft=draft.copy(font=it)}}
      item{AppearanceChoice("Text size","${(draft.scale*100).toInt()}%",listOf("90%","100%","115%","130%")){draft=draft.copy(scale=it.removeSuffix("%").toFloat()/100)}}
      item{AppearanceChoice("Guide density",draft.density,listOf("compact","comfortable","large")){draft=draft.copy(density=it)}}
      item{AppearanceChoice("Corners",draft.corners,listOf("square","soft","rounded")){draft=draft.copy(corners=it)}}
      item{AppearanceChoice("Borders",draft.borders,listOf("clear","strong")){draft=draft.copy(borders=it)}}
      item{AppearanceChoice("Focus",draft.focus,listOf("outline","fill","glow")){draft=draft.copy(focus=it)}}
      item{AppearanceChoice("Artwork",draft.artwork,listOf("all","header","off")){draft=draft.copy(artwork=it)}}
      item{Text("Saved on this device, independent of your provider. Glass uses a lightweight shaded surface on Android; playback is never blurred.",style=MaterialTheme.typography.bodySmall)}
      item{TvTextInput(name,{name=it.take(40)},label={Text("Preset name")},singleLine=true)}
      item{TextButton(onClick={try{Appearance.savePreset(name,draft);revision++;error="Preset saved"}catch(e:Exception){error=e.message?:"Could not save preset"}}){Text("Save named preset")}}
      item{val presets=remember(revision){Appearance.saved()};if(presets.isNotEmpty())AppearanceChoice("Saved presets","choose",presets.map{it.first}){label->draft=presets.first{it.first==label}.second;name=label}}
      item{if(name.isNotBlank()&&Appearance.saved().any{it.first==name})TextButton(onClick={try{Appearance.deletePreset(name);revision++;name=""}catch(e:Exception){error=e.message?:"Could not delete preset"}}){Text("Delete named preset")}}
      item{Row{TextButton(onClick={export.launch("vynode-appearance.json")}){Text("Export")};TextButton(onClick={importLauncher.launch(arrayOf("application/json","text/plain"))}){Text("Import")};TextButton(onClick={draft=AppearanceSettings()}){Text("Reset defaults")}}}
     }
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={original=Appearance.current;Appearance.preview(draft);pending=true}){Text("Apply")};TextButton(onClick={close()}){Text("Cancel")}}
    }
    if(error.isNotBlank())Text(error,style=MaterialTheme.typography.bodySmall)
   }
  }
 }
}
