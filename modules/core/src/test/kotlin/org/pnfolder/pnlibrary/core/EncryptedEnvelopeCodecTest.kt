package org.pnfolder.pnlibrary.core

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.core.security.EncryptedEnvelopeCodec
import java.security.KeyPairGenerator
import java.util.Base64

class EncryptedEnvelopeCodecTest {

    @Test
    fun `encrypt produces valid versioned envelope JSON`() {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()

        val pem = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getMimeEncoder().encodeToString(kp.public.encoded) +
                "\n-----END PUBLIC KEY-----"

        val codec = EncryptedEnvelopeCodec(pem, "test-key-1")
        val sampleReport = """{"status":"ok","server":"Paper 1.20.4"}"""

        val envelopeJson = codec.encrypt(sampleReport)
        assertNotNull(envelopeJson)

        val obj = Gson().fromJson(envelopeJson, JsonObject::class.java)
        assertEquals("pnlibrary-diagnostics", obj.get("format").asString)
        assertEquals(1, obj.get("version").asInt)
        assertEquals("test-key-1", obj.get("keyId").asString)
        assertEquals("RSA-OAEP-256", obj.get("keyAlgorithm").asString)
        assertEquals("A256GCM", obj.get("contentAlgorithm").asString)
        assertEquals("gzip", obj.get("compression").asString)
        assertTrue(obj.has("wrappedKey"))
        assertTrue(obj.has("nonce"))
        assertTrue(obj.has("ciphertext"))
        assertTrue(obj.has("createdUtc"))
    }

    private fun pem(bits: Int): String {
        val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }
        val encoded = Base64.getMimeEncoder().encodeToString(generator.generateKeyPair().public.encoded)
        return "-----BEGIN PUBLIC KEY-----\n$encoded\n-----END PUBLIC KEY-----"
    }

    @Test
    fun `the binary container starts with its marker and the key id`() {
        val bytes = EncryptedEnvelopeCodec(pem(2048), "key-7").encryptBinary("secret".toByteArray(), "json")

        val marker = EncryptedEnvelopeCodec.BINARY_MAGIC
        assertTrue(bytes.copyOfRange(0, marker.size).contentEquals(marker))
        assertTrue(String(bytes, Charsets.ISO_8859_1).contains("key-7"))
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("secret"))
    }

    @Test
    fun `two encryptions of one text differ`() {
        val codec = EncryptedEnvelopeCodec(pem(2048), "k")
        assertNotEquals(codec.encrypt("same"), codec.encrypt("same"))
    }

    @Test
    fun `a weak key and a blank key id are refused`() {
        assertThrows(IllegalStateException::class.java) { EncryptedEnvelopeCodec(pem(1024), "k") }
        assertThrows(IllegalArgumentException::class.java) { EncryptedEnvelopeCodec(pem(2048), "  ") }
    }
}
