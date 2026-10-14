package local.signaltv.app

/** Password changes and logout never change the preference namespace. */
fun accountKey(mode:String,base:String,username:String):String = java.security.MessageDigest.getInstance("SHA-256").digest((mode+"|"+base.trimEnd('/')+"|"+username).toByteArray()).joinToString(""){"%02x".format(it)}
fun Profile.accountKey()=accountKey(mode,base,username)

/** Pairing a companion never replaces a standalone login (or vice versa). */
fun updatedAccounts(existing:List<Profile>,profile:Profile,replacing:Profile?=null):List<Profile> =
 existing.filter{it.accountKey()!=profile.accountKey() && !(replacing?.mode==profile.mode && it.accountKey()==replacing.accountKey())}+profile
