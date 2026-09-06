package ru.privatenull.pnlibrary.core

import ru.privatenull.pnlibrary.core.security.EncryptedEnvelopeCodec


import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.util.Base64

class PythonDecryptionTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `encrypted envelope is successfully decrypted by python decoder`() {
        // Generate RSA 2048 keypair
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()

        val pubPem = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getMimeEncoder().encodeToString(kp.public.encoded) +
                "\n-----END PUBLIC KEY-----"

        val privPem = "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getMimeEncoder().encodeToString(kp.private.encoded) +
                "\n-----END PRIVATE KEY-----"

        val codec = EncryptedEnvelopeCodec(pubPem, "support-1")
        val sampleReport = """{"status":"HEALTHY","activeLots":42,"plugin":"pnMarket"}"""
        val envelopeJson = codec.encrypt(sampleReport)

        val envelopeFile = tempDir.resolve("envelope.pndebug").toFile()
        envelopeFile.writeText(envelopeJson, Charsets.UTF_8)

        val keyFile = tempDir.resolve("private.pem").toFile()
        keyFile.writeText(privPem, Charsets.UTF_8)

        var scriptFile = File("standalone/examples/discord_bot/decrypt_report.py").absoluteFile
        if (!scriptFile.exists()) {
            scriptFile = File("../standalone/examples/discord_bot/decrypt_report.py").absoluteFile
        }
        if (!scriptFile.exists()) {
            println("Skipping PythonDecryptionTest: script not found at ${scriptFile.path}")
            return
        }

        val outputFile = tempDir.resolve("output.json").toFile()

        val pythonCmd = arrayOf(
            "python",
            "-c",
            "import sys, pathlib; sys.path.insert(0, r'${scriptFile.parentFile.absolutePath}'); import decrypt_report; decrypt_report.decrypt_file(pathlib.Path(r'${envelopeFile.absolutePath}'), pathlib.Path(r'${keyFile.absolutePath}'), pathlib.Path(r'${outputFile.absolutePath}'))"
        )

        val pb = ProcessBuilder(*pythonCmd)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val exitCode = proc.waitFor()

        if (exitCode != 0) {
            val output = proc.inputStream.bufferedReader().readText()
            println("Python output: $output")
        }

        assertEquals(0, exitCode, "Python decryption script should exit with code 0")
        assertTrue(outputFile.exists(), "Output file should be created by Python script")
        val decryptedText = outputFile.readText(Charsets.UTF_8)
        assertTrue(decryptedText.contains("HEALTHY"))
        assertTrue(decryptedText.contains("pnMarket"))
    }
}
