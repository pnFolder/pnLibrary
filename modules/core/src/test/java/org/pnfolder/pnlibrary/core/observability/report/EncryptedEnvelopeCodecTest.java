package org.pnfolder.pnlibrary.core.observability.report;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptedEnvelopeCodecTest {

    static String pem(int bits) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        String encoded = Base64.getMimeEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----";
    }

    @Test
    void encryptProducesValidVersionedEnvelopeJson() throws Exception {
        EncryptedEnvelopeCodec codec = new EncryptedEnvelopeCodec(pem(2048), "test-key-1");

        String envelopeJson = codec.encrypt("{\"status\":\"ok\",\"server\":\"Paper 1.20.4\"}");
        assertNotNull(envelopeJson);

        JsonObject obj = new Gson().fromJson(envelopeJson, JsonObject.class);
        assertEquals("pnlibrary-diagnostics", obj.get("format").getAsString());
        assertEquals(1, obj.get("version").getAsInt());
        assertEquals("test-key-1", obj.get("keyId").getAsString());
        assertEquals("RSA-OAEP-256", obj.get("keyAlgorithm").getAsString());
        assertEquals("A256GCM", obj.get("contentAlgorithm").getAsString());
        assertEquals("gzip", obj.get("compression").getAsString());
        assertTrue(obj.has("wrappedKey"));
        assertTrue(obj.has("nonce"));
        assertTrue(obj.has("ciphertext"));
        assertTrue(obj.has("createdUtc"));
    }

    @Test
    void theBinaryContainerStartsWithItsMarkerAndTheKeyId() throws Exception {
        byte[] bytes = new EncryptedEnvelopeCodec(pem(2048), "key-7")
                .encryptBinary("secret".getBytes(StandardCharsets.UTF_8), "json");

        byte[] marker = EncryptedEnvelopeCodec.BINARY_MAGIC;
        assertTrue(Arrays.equals(Arrays.copyOfRange(bytes, 0, marker.length), marker));
        assertTrue(new String(bytes, StandardCharsets.ISO_8859_1).contains("key-7"));
        assertFalse(new String(bytes, StandardCharsets.ISO_8859_1).contains("secret"));
    }

    @Test
    void twoEncryptionsOfOneTextDiffer() throws Exception {
        EncryptedEnvelopeCodec codec = new EncryptedEnvelopeCodec(pem(2048), "k");
        assertNotEquals(codec.encrypt("same"), codec.encrypt("same"));
    }

    @Test
    void aWeakKeyAndABlankKeyIdAreRefused() throws Exception {
        String weak = pem(1024);
        String strong = pem(2048);
        assertThrows(IllegalStateException.class, () -> new EncryptedEnvelopeCodec(weak, "k"));
        assertThrows(IllegalArgumentException.class, () -> new EncryptedEnvelopeCodec(strong, "  "));
    }
}
