package com.confighub.app.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the encryption keys used to protect data at rest.
 *
 * * DB passphrase: random 32 bytes, wrapped with an Android-Keystore AES-GCM key and
 *   persisted as `iv||ciphertext` in a private file.
 * * Field keys: a second Keystore key encrypts config credentials per-field with a
 *   fresh IV.
 *
 * If AndroidKeyStore is unavailable (rare: broken ROM/emulator), the store degrades to
 * an UNENCRYPTED fallback so the app still works; [secure] then reports `false` and the
 * UI surfaces a warning. This is documented in docs/KNOWN_LIMITATIONS.md.
 */
@Singleton
class SecretStore @Inject constructor(@ApplicationContext context: Context) : SecretCrypto {

    private val keysDir: File = File(context.getDir("keys", Context.MODE_PRIVATE), "keystore")
    private val markerPrefs = context.getSharedPreferences("confighub_keys", Context.MODE_PRIVATE)

    override val secure: Boolean = runCatching {
        getOrCreateKey(MASTER_ALIAS)
        getOrCreateKey(FIELD_ALIAS)
        true
    }.getOrElse { false }

    @Volatile
    private var cachedPassphrase: ByteArray? = null

    override fun dbPassphrase(): ByteArray {
        cachedPassphrase?.let { return it }
        synchronized(this) {
            cachedPassphrase?.let { return it }
            keysDir.mkdirs()
            val blobFile = File(keysDir, DB_BLOB_NAME)
            if (blobFile.exists()) {
                val blob = blobFile.readBytes()
                val passphrase = if (blob.size == RAW_KEY_BYTES) {
                    // Written by the insecure fallback path.
                    blob
                } else {
                    require(secure) { "encrypted key blob present but Keystore unavailable" }
                    val iv = blob.copyOfRange(0, GCM_IV_BYTES)
                    decryptWith(MASTER_ALIAS, iv, blob.copyOfRange(GCM_IV_BYTES, blob.size))
                }
                cachedPassphrase = passphrase
                return passphrase
            }
            val fresh = ByteArray(RAW_KEY_BYTES).also { SecureRandom().nextBytes(it) }
            val blob = if (secure) {
                val (iv, ct) = encryptWith(MASTER_ALIAS, fresh)
                iv + ct
            } else {
                fresh
            }
            val tmp = File(keysDir, "$DB_BLOB_NAME.tmp")
            tmp.writeBytes(blob)
            if (!tmp.renameTo(blobFile)) {
                tmp.copyTo(blobFile, overwrite = true)
                tmp.delete()
            }
            cachedPassphrase = fresh
            return fresh
        }
    }

    override fun encryptField(plain: ByteArray): Pair<ByteArray?, ByteArray> {
        if (!secure) return null to plain
        val (iv, ct) = encryptWith(FIELD_ALIAS, plain)
        return iv to ct
    }

    override fun decryptField(iv: ByteArray?, data: ByteArray): ByteArray {
        if (iv == null) return data
        return decryptWith(FIELD_ALIAS, iv, data)
    }

    private fun encryptWith(alias: String, plain: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(alias))
        val iv = cipher.iv
        val ct = cipher.doFinal(plain)
        return iv to ct
    }

    private fun decryptWith(alias: String, iv: ByteArray, ct: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(alias), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        val key = generator.generateKey()
        markerPrefs.edit { putBoolean(alias, true) } // visibility for debugging only
        return key
    }

    companion object {
        private const val MASTER_ALIAS = "confighub_master"
        private const val FIELD_ALIAS = "confighub_fields"
        private const val DB_BLOB_NAME = "db_passphrase.blob"
        private const val RAW_KEY_BYTES = 32
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val KEY_BITS = 256
        const val FALLBACK_WARNING = "AndroidKeyStore unavailable — data at rest is NOT encrypted"
    }
}
