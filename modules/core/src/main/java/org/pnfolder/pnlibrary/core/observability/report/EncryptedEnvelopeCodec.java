package org.pnfolder.pnlibrary.core.observability.report;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Versioned, wire-format-stable diagnostic envelope.
 *
 * <p>Pipeline: {@code JSON UTF-8 -> gzip -> AES-256-GCM -> RSA-OAEP-SHA256 -> URL-safe Base64 -> JSON envelope}.
 * The envelope has the fields {@code format}, {@code version}, {@code keyId}, {@code createdUtc},
 * {@code keyAlgorithm}, {@code contentAlgorithm}, {@code compression}, {@code payloadFormat}, {@code wrappedKey},
 * {@code nonce} and {@code ciphertext}.
 *
 * <p><b>Do not change this format</b>: the Python Discord-bot decoder must be able to decode envelopes produced by
 * every version of this library. The AAD string is fixed:
 * {@code pnlibrary-diagnostics|1|{keyId}|{createdUtc}|RSA-OAEP-256|A256GCM|gzip}.
 */
public final class EncryptedEnvelopeCodec {
    public static final String FORMAT = "pnlibrary-diagnostics";
    public static final int VERSION = 1;
    public static final int BINARY_VERSION = 1;
    public static final byte[] BINARY_MAGIC = "PNSUPPORT\r\n".getBytes(StandardCharsets.US_ASCII);

    private static final Gson JSON = new Gson();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PublicKey publicKey;
    private final String keyId;

    /**
     * Creates a codec.
     *
     * @param publicKeyPem PEM-encoded RSA public key (BEGIN PUBLIC KEY ... END PUBLIC KEY)
     * @param keyId identifier sent to the bot so it knows which private key to use
     * @throws IllegalArgumentException when the key id is blank or longer than 96 characters, or the key is not RSA
     * @throws IllegalStateException when the RSA key is shorter than 2048 bits
     * @throws GeneralSecurityException when the key cannot be parsed
     */
    public EncryptedEnvelopeCodec(@NotNull String publicKeyPem, @NotNull String keyId) throws GeneralSecurityException {
        this.publicKey = parsePublicKey(publicKeyPem);
        String trimmed = keyId.trim();
        if (trimmed.isEmpty() || trimmed.length() > 96) {
            throw new IllegalArgumentException("Invalid key id");
        }
        this.keyId = trimmed;
        if (!(publicKey instanceof RSAPublicKey)) {
            throw new IllegalArgumentException("RSA public key required");
        }
        if (((RSAPublicKey) publicKey).getModulus().bitLength() < 2048) {
            throw new IllegalStateException("RSA public key must be at least 2048 bits");
        }
    }

    /**
     * The additional authenticated data of an envelope. <b>Must match the Python decoder exactly.</b>
     */
    @NotNull
    public static byte[] aad(@NotNull String keyId, @NotNull String createdUtc) {
        return (FORMAT + "|" + VERSION + "|" + keyId + "|" + createdUtc + "|RSA-OAEP-256|A256GCM|gzip")
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Encrypts {@code report} (UTF-8 JSON string) and returns the versioned envelope JSON.
     *
     * @throws IOException on any encryption or I/O failure
     */
    @NotNull
    public String encrypt(@NotNull String report) throws IOException {
        return encrypt(report.getBytes(StandardCharsets.UTF_8), "json");
    }

    /**
     * Encrypts {@code payload} and returns the versioned envelope JSON.
     *
     * @param payloadFormat what the payload is, for the decoder
     * @throws IOException on any encryption or I/O failure
     */
    @NotNull
    public String encrypt(@NotNull byte[] payload, @NotNull String payloadFormat) throws IOException {
        try {
            EncryptedPayload encrypted = encryptPayload(payload);
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("format", FORMAT);
            envelope.put("version", VERSION);
            envelope.put("keyId", keyId);
            envelope.put("createdUtc", encrypted.createdUtc);
            envelope.put("keyAlgorithm", "RSA-OAEP-256");
            envelope.put("contentAlgorithm", "A256GCM");
            envelope.put("compression", "gzip");
            envelope.put("payloadFormat", payloadFormat);
            envelope.put("wrappedKey", base64(encrypted.wrappedKey));
            envelope.put("nonce", base64(encrypted.nonce));
            envelope.put("ciphertext", base64(encrypted.ciphertext));
            return JSON.toJson(envelope);
        } catch (IOException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IOException("Unable to encrypt diagnostics", failure);
        }
    }

    /**
     * Encrypts {@code payload} into the binary container that starts with {@link #BINARY_MAGIC}.
     *
     * @throws IOException on any encryption or I/O failure
     */
    @NotNull
    public byte[] encryptBinary(@NotNull byte[] payload, @NotNull String payloadFormat) throws IOException {
        try {
            EncryptedPayload encrypted = encryptPayload(payload);
            byte[] key = keyId.getBytes(StandardCharsets.UTF_8);
            byte[] created = encrypted.createdUtc.getBytes(StandardCharsets.UTF_8);
            byte[] format = payloadFormat.getBytes(StandardCharsets.UTF_8);
            if (key.length > 65_535 || created.length > 65_535 || format.length > 255) {
                throw new IllegalArgumentException("Failed requirement.");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            writeContainer(output, encrypted, format, key, created);
            return output.toByteArray();
        } catch (IOException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IOException("Unable to encrypt PN Support Archive", failure);
        }
    }

    private static void writeContainer(ByteArrayOutputStream output, EncryptedPayload encrypted, byte[] format,
                                       byte[] key, byte[] created) throws IOException {
        try (DataOutputStream data = new DataOutputStream(output)) {
            data.write(BINARY_MAGIC);
            data.writeShort(BINARY_VERSION);
            data.writeByte(format.length);
            data.writeShort(key.length);
            data.writeShort(created.length);
            data.writeShort(encrypted.nonce.length);
            data.writeInt(encrypted.wrappedKey.length);
            data.writeInt(encrypted.ciphertext.length);
            data.write(format);
            data.write(key);
            data.write(created);
            data.write(encrypted.nonce);
            data.write(encrypted.wrappedKey);
            data.write(encrypted.ciphertext);
        }
    }

    private EncryptedPayload encryptPayload(byte[] payload) throws GeneralSecurityException, IOException {
        byte[] compressed = gzip(payload);
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256, RANDOM);
        SecretKey contentKey = generator.generateKey();
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        String created = Instant.now().toString();
        Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
        aesCipher.init(Cipher.ENCRYPT_MODE, contentKey, new GCMParameterSpec(128, nonce));
        aesCipher.updateAAD(aad(keyId, created));
        byte[] ciphertext = aesCipher.doFinal(compressed);
        OAEPParameterSpec oaep = new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT);
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey, oaep);
        return new EncryptedPayload(created, nonce, rsaCipher.doFinal(contentKey.getEncoded()), ciphertext);
    }

    private static PublicKey parsePublicKey(String pem) throws GeneralSecurityException {
        String stripped = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] encoded = Base64.getDecoder().decode(stripped);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
    }

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream zip = new GZIPOutputStream(out)) {
            zip.write(data);
        }
        return out.toByteArray();
    }

    private static String base64(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
