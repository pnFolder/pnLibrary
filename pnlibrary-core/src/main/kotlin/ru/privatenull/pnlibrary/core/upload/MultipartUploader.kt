package ru.privatenull.pnlibrary.core.upload

import ru.privatenull.pnlibrary.core.security.EncryptedEnvelopeCodec


import com.google.gson.Gson
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/**
 * Handles splitting large encrypted payloads into smaller chunks to respect
 * remote backend limits (e.g. mclo.gs 10 MiB limit).
 */
class MultipartUploader(
    private val uploader: ReportUploader,
    private val encryption: EncryptedEnvelopeCodec?,
    private val uploadLedger: UploadLedger?,
    private val deleteAfterDays: Int = 90,
) {

    @Throws(IOException::class)
    fun upload(payload: String): UploadReceipt {
        val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)
        if (payloadBytes.size <= DIRECT_LIMIT_BYTES || encryption == null || uploader.backendId != "mclogs") {
            return uploader.upload(payload)
        }

        val parts = mutableListOf<Map<String, Any>>()
        var offset = 0
        var order = 0

        while (offset < payload.length) {
            val end = minOf(payload.length, offset + PART_CHARACTERS)
            val chunk = payload.substring(offset, end)
            val receipt = uploader.upload(chunk)
            uploadLedger?.record(receipt, deleteAfterDays)

            parts.add(linkedMapOf(
                "order" to order++,
                "url" to receipt.link.toString(),
                "characters" to chunk.length,
            ))
            offset = end
        }

        val manifest = linkedMapOf<String, Any>(
            "format" to "pnlibrary-diagnostics-index",
            "version" to 1,
            "encoding" to "utf-8",
            "characters" to payload.length,
            "sha256" to sha256(payload),
            "parts" to parts,
        )

        val indexJson = Gson().toJson(manifest)
        val encryptedIndex = encryption.encrypt(indexJson)
        return uploader.upload(encryptedIndex)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(StandardCharsets.UTF_8))
        val sb = StringBuilder()
        for (b in digest) {
            sb.append(String.format(Locale.ROOT, "%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    companion object {
        const val DIRECT_LIMIT_BYTES = 8 * 1024 * 1024 // 8 MiB
        const val PART_CHARACTERS = 7 * 1024 * 1024    // 7 MiB chars
    }
}
