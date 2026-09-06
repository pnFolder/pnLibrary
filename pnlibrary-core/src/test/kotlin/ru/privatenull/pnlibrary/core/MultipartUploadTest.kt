package ru.privatenull.pnlibrary.core

import ru.privatenull.pnlibrary.core.security.EncryptedEnvelopeCodec
import ru.privatenull.pnlibrary.core.upload.MultipartUploader
import ru.privatenull.pnlibrary.core.upload.ReportUploader
import ru.privatenull.pnlibrary.core.upload.UploadLedger
import ru.privatenull.pnlibrary.core.upload.UploadReceipt


import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.security.KeyPairGenerator
import java.util.Base64

class MultipartUploadTest {

    @Test
    fun `splits oversized payload into parts and uploads index`() {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()

        val pem = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getMimeEncoder().encodeToString(kp.public.encoded) +
                "\n-----END PUBLIC KEY-----"

        val codec = EncryptedEnvelopeCodec(pem, "support-1")
        val recordingUploader = RecordingUploader()

        val multipartUploader = MultipartUploader(
            uploader = recordingUploader,
            encryption = codec,
            uploadLedger = null
        )

        // 10 MB payload (> 8 MB direct limit)
        val payload = "A".repeat(10 * 1024 * 1024)
        val receipt = multipartUploader.upload(payload)

        // Should split into 2 chunks + 1 encrypted index manifest = 3 uploads total
        assertEquals(3, recordingUploader.uploadedPayloads.size)
        assertTrue(receipt.link.toString().contains("mclo.gs"))
    }

    private class RecordingUploader : ReportUploader {
        val uploadedPayloads = mutableListOf<String>()

        override val backendId: String get() = "mclogs"

        override fun upload(payload: String): UploadReceipt {
            uploadedPayloads.add(payload)
            val id = "id_${uploadedPayloads.size}"
            return UploadReceipt(
                link = URI.create("https://mclo.gs/$id"),
                id = id,
                deleteToken = "token_${uploadedPayloads.size}",
                createdEpochSeconds = System.currentTimeMillis() / 1000,
                expiresEpochSeconds = 0,
                backend = "mclogs"
            )
        }

        override fun delete(receipt: UploadReceipt): Boolean = true
    }
}
