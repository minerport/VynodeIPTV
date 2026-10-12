package local.signaltv.app

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/** Fixed-size remote targets with labels outside the narrow navigation rail. */
@Composable fun RailIconButton(@DrawableRes icon:Int,label:String,showLabel:Boolean=true,action:()->Unit){
    var focused by remember{mutableStateOf(false)}
    val shape=RoundedCornerShape(10.dp)
    Box(Modifier.size(56.dp),contentAlignment=Alignment.Center){
        IconButton(onClick=action,modifier=Modifier.size(48.dp).onFocusChanged{focused=it.isFocused}
            .background(if(focused)TvPalette.Accent else Color.Transparent,shape)
            .border(if(focused)2.dp else 0.dp,if(focused)Color.White else Color.Transparent,shape)){
            Icon(painterResource(icon),contentDescription=label,modifier=Modifier.size(26.dp),tint=if(focused)TvPalette.OnAccent else TvPalette.Accent)
        }
        if(focused && showLabel){
            val offset=with(LocalDensity.current){60.dp.roundToPx()}
            Popup(alignment=Alignment.CenterStart,offset=IntOffset(offset,0),properties=PopupProperties(focusable=false)){
                Surface(shape=shape,color=TvPalette.Surface,shadowElevation=8.dp){
                    Text(label,Modifier.padding(horizontal=14.dp,vertical=10.dp),maxLines=1,softWrap=false,style=MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
