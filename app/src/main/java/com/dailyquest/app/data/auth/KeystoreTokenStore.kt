package com.dailyquest.app.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore의 AES-256/GCM 키(추출 불가)로 토큰을 암호화해 SharedPreferences에 저장한다.
 * 두 토큰을 한 덩어리로 암호화하고 commit()으로 한 번에 기록해 항상 일관된 쌍만 저장된다.
 * 복호화에 실패하면(키 손실·변조) 저장값을 지우고 로그아웃 상태로 취급한다.
 */
class KeystoreTokenStore(context: Context) : TokenStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(): TokenPair? {
        val blob = prefs.getString(KEY_BLOB, null) ?: return null
        return try {
            val parts = blob.split(':')
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val data = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            val tokens = String(cipher.doFinal(data), Charsets.UTF_8).split('\n')
            if (tokens.size == 2 && tokens.all { it.isNotEmpty() }) TokenPair(tokens[0], tokens[1]) else null.also { clear() }
        } catch (e: Exception) {
            clear()
            null
        }
    }

    override fun write(tokens: TokenPair) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal("${tokens.accessToken}\n${tokens.refreshToken}".toByteArray(Charsets.UTF_8))
        val blob = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        check(prefs.edit().putString(KEY_BLOB, blob).commit()) { "token write failed" }
    }

    override fun clear() {
        prefs.edit().remove(KEY_BLOB).commit()
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val PREFS_NAME = "dq_auth_secure"
        private const val KEY_BLOB = "tokens"
        private const val KEY_ALIAS = "dq_auth_aes_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
    }
}
