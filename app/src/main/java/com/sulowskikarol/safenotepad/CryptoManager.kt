package com.sulowskikarol.safenotepad

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class CryptoManager {

    private val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
    }

    private fun getKey(): SecretKey {
        val existingKey = keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: createKey()
    }

    private fun createKey(): SecretKey {
        return KeyGenerator.getInstance(ALGORITHM, KEYSTORE_PROVIDER).apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(BLOCK_MODE)
                    .setEncryptionPaddings(PADDING)
                    .setUserAuthenticationRequired(false)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }

    fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getKey())
        }
        val encrypted = cipher.doFinal(data)
        return cipher.iv + encrypted
    }

    fun decrypt(data: ByteArray): ByteArray {
        if (data.size < IV_SIZE) {
            throw IllegalArgumentException("Encrypted data is too short or corrupted (missing IV)")
        }
        val iv = data.copyOfRange(0, IV_SIZE)
        val encryptedData = data.copyOfRange(IV_SIZE, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(TAG_LENGTH, iv))
        }
        return cipher.doFinal(encryptedData)
    }

    fun encryptWithPassword(data: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_SIZE).apply {
            SecureRandom().nextBytes(this)
        }
        val key = deriveKeyFromPassword(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
        val encrypted = cipher.doFinal(data)
        // Return [Salt (16 bytes)] + [IV (12 bytes)] + [Ciphertext]
        return salt + cipher.iv + encrypted
    }

    fun decryptWithPassword(data: ByteArray, password: CharArray): ByteArray {
        if (data.size < SALT_SIZE + IV_SIZE) {
            throw IllegalArgumentException("Data is too short to contain salt and IV")
        }
        val salt = data.copyOfRange(0, SALT_SIZE)
        val iv = data.copyOfRange(SALT_SIZE, SALT_SIZE + IV_SIZE)
        val encryptedData = data.copyOfRange(SALT_SIZE + IV_SIZE, data.size)

        val key = deriveKeyFromPassword(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))
        }
        return cipher.doFinal(encryptedData)
    }

    private fun deriveKeyFromPassword(password: CharArray, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, ALGORITHM)
    }

    fun deleteKey() {
        keyStore.deleteEntry(ALIAS)
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
        private const val ALIAS = "NotepadKey"

        private const val IV_SIZE = 12
        private const val TAG_LENGTH = 128

        private const val SALT_SIZE = 16
        private const val PBKDF2_ITERATIONS = 100000
        private const val KEY_LENGTH = 256
    }
}