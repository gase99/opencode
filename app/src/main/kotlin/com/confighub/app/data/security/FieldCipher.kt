package com.confighub.app.data.security

import javax.inject.Inject
import javax.inject.Singleton

/** Abstraction over field-level credential encryption (swappable for tests). */
interface SecretCrypto {
    val secure: Boolean

    /** Encrypts a per-field credential. Returns (iv, ciphertext); iv == null → plaintext fallback. */
    fun encryptField(plain: ByteArray): Pair<ByteArray?, ByteArray>

    /** Decrypts a per-field credential produced by [encryptField]. */
    fun decryptField(iv: ByteArray?, data: ByteArray): ByteArray

    /** Returns (creates on first use) the SQLCipher database passphrase. */
    fun dbPassphrase(): ByteArray
}

/**
 * Encrypts/decrypts individual config credentials for storage.
 *
 * When the underlying [SecretCrypto] is in insecure-fallback mode, `iv == null`
 * marks plaintext storage (the app still functions; the user is warned in Settings).
 */
@Singleton
class FieldCipher @Inject constructor(private val crypto: SecretCrypto) {

    data class Encrypted(val iv: ByteArray?, val data: ByteArray) {
        val isEncrypted: Boolean get() = iv != null
    }

    fun encrypt(plain: ByteArray): Encrypted {
        val (iv, data) = crypto.encryptField(plain)
        return Encrypted(iv, data)
    }

    fun decrypt(value: Encrypted): ByteArray = crypto.decryptField(value.iv, value.data)

    fun decrypt(iv: ByteArray?, data: ByteArray): ByteArray = crypto.decryptField(iv, data)
}
