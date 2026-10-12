package local.signaltv.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** A remote-focusable control; only activation creates an editable text field. */
@Composable fun TvTextInput(value:String,onValueChange:(String)->Unit,modifier:Modifier=Modifier,
    label:(@Composable ()->Unit)?=null,placeholder:(@Composable ()->Unit)?=null,
    singleLine:Boolean=true,compact:Boolean=false,visualTransformation:VisualTransformation=VisualTransformation.None){
    var focused by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf(false)}
    var draft by remember{mutableStateOf(value)}
    OutlinedButton(onClick={draft=value;editing=true},modifier=modifier.heightIn(min=if(compact)48.dp else 56.dp).onFocusChanged{focused=it.isFocused}.border(if(focused)2.dp else 0.dp,if(focused)Color.White else Color.Transparent)){
        Column(Modifier.fillMaxWidth()){
            label?.invoke()
            if(value.isEmpty())placeholder?.invoke()
            else Text(visualTransformation.filter(AnnotatedString(value)).text.text,maxLines=1)
            if(value.isEmpty() && placeholder==null)Text("Select to enter",style=MaterialTheme.typography.bodySmall)
        }
    }
    if(editing){
        val focus=remember{FocusRequester()};val keyboard=LocalSoftwareKeyboardController.current
        AlertDialog(onDismissRequest={editing=false},title={label?.invoke()?:Text("Enter text")},text={
            OutlinedTextField(draft,{draft=it},modifier=Modifier.focusRequester(focus),singleLine=singleLine,visualTransformation=visualTransformation)
        },confirmButton={Button(onClick={onValueChange(draft);keyboard?.hide();editing=false}){Text("Done")}},dismissButton={TextButton(onClick={keyboard?.hide();editing=false}){Text("Cancel")}})
        LaunchedEffect(Unit){focus.requestFocus();keyboard?.show()}
    }
}
