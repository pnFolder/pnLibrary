package org.pnfolder.pnlibrary.core.observability.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonDecryptionTest {
    @TempDir
    Path tempDir;

    @Test
    void encryptedEnvelopeIsSuccessfullyDecryptedByPythonDecoder() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String pubPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(pair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
        String privPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";

        EncryptedEnvelopeCodec codec = new EncryptedEnvelopeCodec(pubPem, "support-1");
        String envelopeJson = codec.encrypt("{\"status\":\"HEALTHY\",\"activeLots\":42,\"plugin\":\"pnMarket\"}");

        File envelopeFile = tempDir.resolve("envelope.pndebug").toFile();
        Files.write(envelopeFile.toPath(), envelopeJson.getBytes(StandardCharsets.UTF_8));
        File keyFile = tempDir.resolve("private.pem").toFile();
        Files.write(keyFile.toPath(), privPem.getBytes(StandardCharsets.UTF_8));

        File scriptFile = new File("standalone/examples/discord_bot/decrypt_report.py").getAbsoluteFile();
        if (!scriptFile.exists()) {
            scriptFile = new File("../standalone/examples/discord_bot/decrypt_report.py").getAbsoluteFile();
        }
        if (!scriptFile.exists()) {
            System.out.println("Skipping PythonDecryptionTest: script not found at " + scriptFile.getPath());
            return;
        }

        File outputFile = tempDir.resolve("output.json").toFile();
        String[] command = {
                "python",
                "-c",
                "import sys, pathlib; "
                        + "sys.path.insert(0, r'" + scriptFile.getParentFile().getAbsolutePath() + "'); "
                        + "import decrypt_report; "
                        + "decrypt_report.decrypt_file("
                        + "pathlib.Path(r'" + envelopeFile.getAbsolutePath() + "'), "
                        + "pathlib.Path(r'" + keyFile.getAbsolutePath() + "'), "
                        + "pathlib.Path(r'" + outputFile.getAbsolutePath() + "'))"
        };

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            for (int read = process.getInputStream().read(chunk); read >= 0; read = process.getInputStream().read(chunk)) {
                output.write(chunk, 0, read);
            }
            System.out.println("Python output: " + new String(output.toByteArray(), StandardCharsets.UTF_8));
        }

        assertEquals(0, exitCode, "Python decryption script should exit with code 0");
        assertTrue(outputFile.exists(), "Output file should be created by Python script");
        String decryptedText = new String(Files.readAllBytes(outputFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(decryptedText.contains("HEALTHY"));
        assertTrue(decryptedText.contains("pnMarket"));
    }
}
