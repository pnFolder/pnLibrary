package ru.privatenull.pnlibrary.core.security

import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.LinkedHashMap
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import java.security.spec.MGF1ParameterSpec

/**
 * Versioned, wire-format–stable diagnostic envelope.
 *
 * Pipeline: `JSON UTF-8 → gzip → AES-256-GCM → RSA-OAEP-SHA256 → URL-safe Base64 → JSON envelope`
 *
 * The resulting JSON structure is:
 * ```json
 * {
 *   "format":           "pnlibrary-diagnostics",
 *   "version":          1,
 *   "keyId":            "<id>",
 *   "createdUtc":       "<ISO-8601>",
 *   "keyAlgorithm":     "RSA-OAEP-256",
 *   "contentAlgorithm": "A256GCM",
 *   "compression":      "gzip",
 *   "wrappedKey":       "<url-safe base64>",
 *   "nonce":            "<url-safe base64>",
 *   "ciphertext":       "<url-safe base64>"
 * }
 * ```
 *
 * **Do not change this format** — the Python Discord-bot decoder must be
 * able to decode envelopes produced by every version of this library.
 * The AAD string is fixed: `pnlibrary-diagnostics|1|{keyId}|{createdUtc}|RSA-OAEP-256|A256GCM|gzip`
 *
 * @param publicKeyPem  PEM-encoded RSA public key (BEGIN PUBLIC KEY … END PUBLIC KEY).
 * @param keyId         Identifier sent to the bot so it knows which private key to use.
 */
class EncryptedEnvelopeCodec(publicKeyPem: String, keyId: String) {

    private val publicKey: PublicKey = parsePublicKey(publicKeyPem)
    private val keyId: String = require(keyId.trim(), "key id", 96)

    init {
        val rsa = publicKey as? RSAPublicKey
            ?: throw IllegalArgumentException("RSA public key required")
        check(rsa.modulus.bitLength() >= 2048) { "RSA public key must be at least 2048 bits" }
    }

    /**
     * Encrypts [report] (UTF-8 JSON string) and returns the versioned envelope JSON.
     *
     * @throws IOException on any encryption or I/O failure.
     */
    @Throws(IOException::class)
    fun encrypt(report: String): String {
        return encrypt(report.toByteArray(StandardCharsets.UTF_8), "json")
    }

    /** Encrypts arbitrary diagnostic bytes, including a ZIP archive. */
    @Throws(IOException::class)
    fun encrypt(payload: ByteArray, payloadFormat: String): String {
        try {
            val encrypted = encryptPayload(payload)

            val envelope = LinkedHashMap<String, Any>()
            envelope["format"]           = FORMAT
            envelope["version"]          = VERSION
            envelope["keyId"]            = keyId
            envelope["createdUtc"]       = encrypted.createdUtc
            envelope["keyAlgorithm"]     = "RSA-OAEP-256"
            envelope["contentAlgorithm"] = "A256GCM"
            envelope["compression"]      = "gzip"
            envelope["payloadFormat"]    = payloadFormat
            envelope["wrappedKey"]       = base64(encrypted.wrappedKey)
            envelope["nonce"]            = base64(encrypted.nonce)
            envelope["ciphertext"]       = base64(encrypted.ciphertext)
            return JSON.toJson(envelope)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Unable to encrypt diagnostics", e)
        }
    }

    /** Produces the opaque PN Support Archive binary container used for reports and history files. */
    @Throws(IOException::class)
    fun encryptBinary(payload: ByteArray, payloadFormat: String): ByteArray {
        try {
            val encrypted = encryptPayload(payload)
            val key = keyId.toByteArray(StandardCharsets.UTF_8)
            val created = encrypted.createdUtc.toByteArray(StandardCharsets.UTF_8)
            val format = payloadFormat.toByteArray(StandardCharsets.UTF_8)
            require(key.size <= 65_535 && created.size <= 65_535 && format.size <= 255)
            val output = ByteArrayOutputStream()
            DataOutputStream(output).use { data ->
                data.write(BINARY_MAGIC)
                data.writeShort(BINARY_VERSION)
                data.writeByte(format.size)
                data.writeShort(key.size)
                data.writeShort(created.size)
                data.writeShort(encrypted.nonce.size)
                data.writeInt(encrypted.wrappedKey.size)
                data.writeInt(encrypted.ciphertext.size)
                data.write(format)
                data.write(key)
                data.write(created)
                data.write(encrypted.nonce)
                data.write(encrypted.wrappedKey)
                data.write(encrypted.ciphertext)
            }
            return output.toByteArray()
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Unable to encrypt PN Support Archive", e)
        }
    }

    private fun encryptPayload(payload: ByteArray): EncryptedPayload {
        val compressed = gzip(payload)
        val contentKey: SecretKey = KeyGenerator.getInstance("AES").also { it.init(256, RANDOM) }.generateKey()
        val nonce = ByteArray(12).also { RANDOM.nextBytes(it) }
        val created = Instant.now().toString()
        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(Cipher.ENCRYPT_MODE, contentKey, GCMParameterSpec(128, nonce))
        aesCipher.updateAAD(aad(keyId, created))
        val ciphertext = aesCipher.doFinal(compressed)
        val oaepSpec = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)
        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepSpec)
        return EncryptedPayload(created, nonce, rsaCipher.doFinal(contentKey.encoded), ciphertext)
    }

    private data class EncryptedPayload(
        val createdUtc: String,
        val nonce: ByteArray,
        val wrappedKey: ByteArray,
        val ciphertext: ByteArray,
    )

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        const val FORMAT  = "pnlibrary-diagnostics"
        const val VERSION = 1
        const val BINARY_VERSION = 1
        val BINARY_MAGIC: ByteArray = "PNSUPPORT\r\n".toByteArray(StandardCharsets.US_ASCII)

        private val JSON   = Gson()
        private val RANDOM = SecureRandom()

        /**
         * Builds the Additional Authenticated Data string.
         * **Must match the Python decoder exactly.**
         */
        @JvmStatic
        fun aad(keyId: String, createdUtc: String): ByteArray =
            "$FORMAT|$VERSION|$keyId|$createdUtc|RSA-OAEP-256|A256GCM|gzip"
                .toByteArray(StandardCharsets.UTF_8)

        private fun parsePublicKey(pem: String): PublicKey {
            val stripped = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), "")
            val encoded = Base64.getDecoder().decode(stripped)
            return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(encoded))
        }

        private fun gzip(data: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            GZIPOutputStream(out).use { it.write(data) }
            return out.toByteArray()
        }

        private fun base64(data: ByteArray): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(data)

        private fun require(value: String, label: String, max: Int): String {
            require(value.isNotBlank() && value.length <= max) { "Invalid $label" }
            return value
        }
    }
}
