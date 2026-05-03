package com.feifan.fuckingnjit.utils.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.InvalidAlgorithmParameterException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher

class SecureUtil {
    private val ANDROID_KEYSTORE = "AndroidKeyStore"
    private var keyStore: KeyStore

    init {
        try {
            keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
        } catch (e: Exception) {
            throw RuntimeException("Failed to initialize KeyStore", e)
        }
    }

    companion object {
        @Throws(
            NoSuchProviderException::class,
            NoSuchAlgorithmException::class,
            InvalidAlgorithmParameterException::class
        )
        fun initKeyPair() {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (!keyStore.containsAlias("fuckingnjit_key")) {
                val keyPairGenerator = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore"
                )

                val builder = KeyGenParameterSpec.Builder(
                    "fuckingnjit_key",
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setUserAuthenticationRequired(false)
                    .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                    .setKeySize(2048)

                keyPairGenerator.initialize(builder.build())
                keyPairGenerator.generateKeyPair()
            }
        }

        @get:Throws(Exception::class)
        private val publicKey: PublicKey
            /**
             * 获取公钥用于加密
             */
            get() {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                if (!keyStore.containsAlias("fuckingnjit_key")) {
                    initKeyPair()
                }
                val entry = keyStore.getEntry("fuckingnjit_key", null)
                if (entry is KeyStore.PrivateKeyEntry) {
                    return entry.certificate.publicKey
                }
                throw Exception("Failed to get public key")
            }

        @get:Throws(Exception::class)
        private val privateKey: PrivateKey
            /**
             * 获取私钥用于解密
             */
            get() {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                if (!keyStore.containsAlias("fuckingnjit_key")) {
                    initKeyPair()
                }
                val entry = keyStore.getEntry("fuckingnjit_key", null)
                if (entry is KeyStore.PrivateKeyEntry) {
                    return entry.privateKey
                }
                throw Exception("Failed to get private key")
            }

        /**
         * RSA加密
         */
        fun rsaEncrypt(plaintext: String): String {
            try {
                val publicKey = publicKey
                val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
                cipher.init(Cipher.ENCRYPT_MODE, publicKey)
                val encryptedBytes = cipher.doFinal(plaintext.toByteArray())
                return Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return plaintext
        }

        /**
         * RSA解密
         */
        fun rsaDecrypt(ciphertext: String): String {
            if (ciphertext.isEmpty()) {
                return ""
            }
            try {
                val privateKey = privateKey
                val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
                cipher.init(Cipher.DECRYPT_MODE, privateKey)
                val decodedBytes = Base64.decode(ciphertext, Base64.DEFAULT)
                val decryptedBytes = cipher.doFinal(decodedBytes)
                return String(decryptedBytes)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return ciphertext
        }
    }
}