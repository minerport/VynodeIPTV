package local.signaltv.app

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*

fun Modifier.guideSelectKeys(watch:()->Unit,menu:()->Unit):Modifier=composed {
    val press=remember{GuideSelectPress()}
    val currentWatch by rememberUpdatedState(watch)
    val currentMenu by rememberUpdatedState(menu)
    onFocusChanged{if(!it.hasFocus)press.reset()}.onPreviewKeyEvent{event->
        if(event.nativeKeyEvent.keyCode==android.view.KeyEvent.KEYCODE_MENU){
            if(event.type==KeyEventType.KeyUp)currentMenu();true
        }else if(event.key==Key.Enter||event.key==Key.DirectionCenter||event.key==Key.NumPadEnter){
            val native=event.nativeKeyEvent
            // Android remotes may signal FLAG_LONG_PRESS before 500 ms according
            // to the device's accessibility/key timeout. Honor that signal too.
            val elapsed=if(native.isLongPress)500L else (native.eventTime-native.downTime).coerceAtLeast(0)
            val action=if(event.type==KeyEventType.KeyDown)press.down(native.repeatCount,elapsed)else press.up(elapsed,native.isCanceled)
            when(action){GuideSelectPress.Action.WATCH->currentWatch();GuideSelectPress.Action.MENU->currentMenu();else->Unit};true
        }else false
    }
}
