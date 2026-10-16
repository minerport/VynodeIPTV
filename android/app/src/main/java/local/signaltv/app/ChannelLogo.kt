package local.signaltv.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private val logoCache=object:LruCache<String,Bitmap>(4*1024*1024){override fun sizeOf(key:String,value:Bitmap)=value.byteCount}
private val logoRequests=Semaphore(2)
@Composable fun ChannelLogo(url:String,modifier:Modifier=Modifier){
    var bitmap by remember(url){mutableStateOf(logoCache.get(url))}
    LaunchedEffect(url){
        if(safeChannelLogo(url).isEmpty()||bitmap!=null)return@LaunchedEffect
        bitmap=logoRequests.withPermit{withContext(Dispatchers.IO){runCatching{
            val c=java.net.URL(url).openConnection() as java.net.HttpURLConnection
            try{
                c.instanceFollowRedirects=false;c.connectTimeout=4000;c.readTimeout=4000
                check(c.responseCode==200)
                val bytes=c.inputStream.use{it.readBytesLimited(512*1024)}
                val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                check(bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192)
                var sample=1;while(bounds.outWidth/sample>128||bounds.outHeight/sample>128)sample*=2
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample})?.also{logoCache.put(url,it)}
            }finally{c.disconnect()}
        }.getOrNull()}}
    }
    bitmap?.let{Image(it.asImageBitmap(),contentDescription=null,modifier=modifier,contentScale=ContentScale.Fit)}
}
