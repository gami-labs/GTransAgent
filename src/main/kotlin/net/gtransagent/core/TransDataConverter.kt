package net.gtransagent.core

import net.gtransagent.grpc.LangItem
import net.gtransagent.grpc.ResultItem
import org.apache.commons.codec.binary.Base64
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Convert the data between TransItem and ResultItem
 */
object TransDataConverter {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private const val AES_MODE = "AES/CBC/PKCS5Padding"
    private const val IV_SIZE = 16  // AES块大小固定为16字节
    private const val KEY_SIZE = 16  // AES-128密钥长度

    // 加密格式：[IV(16字节)] + [密文]
    private fun encrypt(plaintext: ByteArray, keyStr: String): ByteArray {
        try {
            val key: ByteArray = keyStr.toByteArray(StandardCharsets.UTF_8)
            validateKey(key)
            val iv = generateSecureIv()

            val cipher = Cipher.getInstance(AES_MODE).apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            }

            return iv + cipher.doFinal(plaintext)
        } catch (e: Exception) {
            logger.error("encrypt error: ${e.message}", e)
            throw e
        }
    }

    // 解密格式：[IV(16字节)] + [密文]
    private fun decrypt(encryptedData: ByteArray, keyStr: String): ByteArray {
        try {
            val key: ByteArray = keyStr.toByteArray(StandardCharsets.UTF_8)

            validateKey(key)
            require(encryptedData.size >= IV_SIZE) { "Invalid encrypted data" }

            val iv = encryptedData.copyOfRange(0, IV_SIZE)
            val ciphertext = encryptedData.copyOfRange(IV_SIZE, encryptedData.size)

            return Cipher.getInstance(AES_MODE).apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            }.doFinal(ciphertext)
        } catch (e: Exception) {
            logger.error("decrypt error: ${e.message}")
            throw e
        }
    }

    private fun generateSecureIv(): ByteArray {
        return ByteArray(IV_SIZE).apply { SecureRandom().nextBytes(this) }
    }

    private fun validateKey(key: ByteArray) {
        require(key.size == KEY_SIZE) {
            "Invalid AES-128 key length: ${key.size} bytes (required: 16 bytes)"
        }
    }


    fun encryptResultData(items: List<ResultItem>): List<String> {
        return items.map {
            Base64.encodeBase64String(encrypt(it.toByteArray(), SecurityKeyAccessor.getSecurityKey()))
        }
    }

    fun encryptData(items: List<LangItem>): List<String> {
        return items.map {
            encryptData(it.toByteArray())
        }
    }

    fun encryptData(input: ByteArray): String {
        return Base64.encodeBase64String(
            encrypt(
                input, SecurityKeyAccessor.getSecurityKey()
            )
        )
    }

    fun decryptData(input: String): ByteArray {
        return decrypt(Base64.decodeBase64(input), SecurityKeyAccessor.getSecurityKey())
    }

    fun decryptData(items: List<String>): List<LangItem> {
        return items.map {
            if (it.isNotBlank()) {
                val bytes = decryptData(it)
                LangItem.parseFrom(bytes)
            } else {
                LangItem.getDefaultInstance()
            }
        }
    }

    fun decryptResultData(items: List<String>): List<ResultItem> {
        return items.map {
            if (it.isNotBlank()) {
                val bytes = decryptData(it)
                ResultItem.parseFrom(bytes)
            } else {
                ResultItem.getDefaultInstance()
            }
        }
    }
}