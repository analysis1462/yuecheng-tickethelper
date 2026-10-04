package com.yuecheng.ticket.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 敏感字段加密存取(AES-256/GCM)。
 * API 23+:Android Keystore,密钥不出系统密钥库;
 * API 21/22:Keystore 不支持 AES,退化为随机软件密钥存应用私有 Preferences。
 * 任何异常(密钥被系统清除、数据损坏等)都返回 null,调用方按"无凭据"降级,不抛出。
 */
object SecureStore {
    private const val ALIAS = "yc_secure_v1"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    @Volatile
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun secretKey(): SecretKey? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) keystoreKey() else softwareKey()

    @RequiresApi(Build.VERSION_CODES.M)
    private fun keystoreKey(): SecretKey? = runCatching {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(
                    KeyGenParameterSpec.Builder(
                        ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
            }.generateKey()
    }.getOrNull()

    private fun softwareKey(): SecretKey? = runCatching {
        val prefs = appContext.getSharedPreferences("yc_secure_sw", Context.MODE_PRIVATE)
        val stored = prefs.getString("key", null)
        val raw = stored?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: ByteArray(32).also {
                SecureRandom().nextBytes(it)
                prefs.edit().putString("key", Base64.encodeToString(it, Base64.NO_WRAP)).apply()
            }
        SecretKeySpec(raw, "AES")
    }.getOrNull()

    /** 加密为 Base64(iv + ciphertext);失败返回 null */
    fun encrypt(plain: String): String? = runCatching {
        val key = secretKey() ?: return@runCatching null
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }.getOrNull()

    /** 解密;数据为空或不可解时返回 null */
    fun decrypt(encoded: String?): String? = runCatching {
        if (encoded.isNullOrEmpty()) return@runCatching null
        val all = Base64.decode(encoded, Base64.NO_WRAP)
        if (all.size <= IV_LEN) return@runCatching null
        val key = secretKey() ?: return@runCatching null
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, all, 0, IV_LEN))
        String(cipher.doFinal(all, IV_LEN, all.size - IV_LEN), Charsets.UTF_8)
    }.getOrNull()
}
