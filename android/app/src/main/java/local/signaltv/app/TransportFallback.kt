package local.signaltv.app

fun fallbackAddress(value:String):String? = runCatching{
 val u=java.net.URI(value);if(u.scheme!="https" || u.host==null || u.rawUserInfo!=null)return null
 "http://"+u.rawAuthority.removeSuffix(":443")+u.rawPath.orEmpty()+(u.rawQuery?.let{"?"+it}?:"")
}.getOrNull()
fun transportFailure(error:Throwable):Boolean = generateSequence(error){it.cause}.take(12).any{it is javax.net.ssl.SSLException || it is java.net.ConnectException || it is java.net.SocketTimeoutException}
