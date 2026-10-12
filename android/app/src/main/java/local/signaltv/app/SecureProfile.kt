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
    fun save(profile: Profile) { val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());val b=c.iv+c.doFinal(profile.json().toString().toByteArray());check(prefs.edit().putString("profile",Base64.encodeToString(b,Base64.NO_WRAP)).commit()) }
    fun load(): Profile? { val encoded=prefs.getString("profile",null)?:return null;return try { val b=Base64.decode(encoded,Base64.NO_WRAP);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,b.copyOfRange(0,12)));Profile.from(JSONObject(String(c.doFinal(b.copyOfRange(12,b.size))))) }catch(_:Exception){throw IllegalStateException("Saved connection could not be unlocked. Retry or explicitly reset the login.")} }
    fun clear(){prefs.edit().clear().apply()}
}
