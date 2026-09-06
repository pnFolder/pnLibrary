package ru.privatenull.pnlibrary.core

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
}
