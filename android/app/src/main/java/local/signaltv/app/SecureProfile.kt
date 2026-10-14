package local.signaltv.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureProfile(context: Context) {
    private val prefs=context.getSharedPreferences("connection",Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("signal-profile",null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply { init(KeyGenParameterSpec.Builder("signal-profile",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()) }.generateKey()
    }
    private fun encrypt(value:String):String {val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());return Base64.encodeToString(c.iv+c.doFinal(value.toByteArray()),Base64.NO_WRAP)}
    private fun decrypt(value:String):String {try{val b=Base64.decode(value,Base64.NO_WRAP);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,b.copyOfRange(0,12)));return String(c.doFinal(b.copyOfRange(12,b.size)))}catch(_:Exception){throw IllegalStateException("Saved accounts could not be unlocked. Retry or explicitly reset saved accounts.")}}
    @Synchronized fun accounts():List<Profile>{
        val saved=prefs.getString("accounts",null)
        if(saved!=null){val a=org.json.JSONArray(decrypt(saved));return (0 until a.length()).map{Profile.from(a.getJSONObject(it))}}
        val legacy=prefs.getString("profile",null)?.let{Profile.from(JSONObject(decrypt(it)))}?:return emptyList()
        write(listOf(legacy),legacy.accountKey());return listOf(legacy)
    }
    private fun write(all:List<Profile>,active:String?){val e=prefs.edit().putString("accounts",encrypt(org.json.JSONArray(all.map{it.json()}).toString())).remove("profile");if(active==null)e.remove("active")else e.putString("active",active);check(e.commit()){"Could not save accounts"}}
    @Synchronized fun save(profile:Profile,replacing:Profile?=null){val all=updatedAccounts(accounts(),profile,replacing);require(all.size<=20){"Maximum 20 saved accounts"};write(all,profile.accountKey())}
    @Synchronized fun load():Profile? {val all=accounts();val active=prefs.getString("active",null);return all.find{it.accountKey()==active}}
    @Synchronized fun logout(){accounts();check(prefs.edit().remove("active").commit())}
    @Synchronized fun tmdb():JSONObject = prefs.getString("tmdb",null)?.let{JSONObject(decrypt(it))}?:JSONObject()
    @Synchronized fun saveTmdb(value:JSONObject){check(prefs.edit().putString("tmdb",encrypt(value.toString())).commit()){"Could not save TMDB connection"}}
    @Synchronized fun remove(profile:Profile){val active=prefs.getString("active",null);write(accounts().filter{it.accountKey()!=profile.accountKey()},active?.takeUnless{it==profile.accountKey()})}
    fun clear(){check(prefs.edit().clear().commit())}
}
