package org.pnfolder.pnlibrary.core.journal

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Binary block format of a journal file.
 *
 * A file is a sequence of blocks with no file header, so any prefix of a file stays readable:
 * ```
 * block := u32 payloadLength | u32 crc32(payload) | payload
 * payload := gzip( u32 recordCount, record* )
 * record := i64 timeMillis | utf level | utf source | utf type | utf message | u16 detailCount | (utf key | utf value)*
 * ```
 * Each block is independently compressed and checksummed. A damaged block is skipped by its length,
 * and a truncated tail (power loss during a write) is ignored, so earlier blocks always survive.
 */
internal object JournalBlockCodec {
    private const val HEADER_BYTES = 8
    private const val MAX_PAYLOAD_BYTES = 16 * 1024 * 1024

    /** Encodes [records] as one complete block. */
    fun encode(records: List<JournalRecord>): ByteArray {
        val raw = ByteArrayOutputStream()
        GZIPOutputStream(raw).use { gzip ->
            DataOutputStream(gzip).use { out ->
                out.writeInt(records.size)
                records.forEach { record -> writeRecord(out, record) }
            }
        }
        val payload = raw.toByteArray()
        require(payload.size <= MAX_PAYLOAD_BYTES) { "Journal block is too large: ${payload.size} bytes" }
        val crc = CRC32().apply { update(payload) }.value
        val block = ByteArrayOutputStream(HEADER_BYTES + payload.size)
        DataOutputStream(block).use { out ->
            out.writeInt(payload.size)
            out.writeInt(crc.toInt())
            out.write(payload)
        }
        return block.toByteArray()
    }

    /**
     * Decodes every intact block in [bytes]. Blocks with a bad checksum or unreadable payload are skipped;
     * decoding stops at the first incomplete block, which is what an interrupted write leaves behind.
     */
    fun decodeAll(bytes: ByteArray): List<JournalRecord> {
        val result = mutableListOf<JournalRecord>()
        var offset = 0
        while (bytes.size - offset >= HEADER_BYTES) {
            val header = DataInputStream(ByteArrayInputStream(bytes, offset, HEADER_BYTES))
            val length = header.readInt()
            val expectedCrc = header.readInt().toLong() and 0xFFFF_FFFFL
            if (length < 0 || length > MAX_PAYLOAD_BYTES) break
            val end = offset + HEADER_BYTES + length
            if (end > bytes.size) break
            val payload = bytes.copyOfRange(offset + HEADER_BYTES, end)
            offset = end
            if (CRC32().apply { update(payload) }.value != expectedCrc) continue
            result += runCatching { decodePayload(payload) }.getOrElse { emptyList() }
        }
        return result
    }

    private fun decodePayload(payload: ByteArray): List<JournalRecord> =
        DataInputStream(GZIPInputStream(ByteArrayInputStream(payload))).use { input ->
            val count = input.readInt()
            List(count) { readRecord(input) }
        }

    private fun writeRecord(out: DataOutputStream, record: JournalRecord) {
        out.writeLong(record.timeMillis)
        out.writeUTF(record.level.name)
        out.writeUTF(record.source.take(JournalRecord.MAX_DETAIL_CHARS))
        out.writeUTF(record.type.take(JournalRecord.MAX_DETAIL_CHARS))
        out.writeUTF(record.message.take(JournalRecord.MAX_MESSAGE_CHARS))
        val details = record.details.entries.take(MAX_DETAILS)
        out.writeShort(details.size)
        details.forEach { (key, value) ->
            out.writeUTF(key.take(JournalRecord.MAX_DETAIL_CHARS))
            out.writeUTF(value.take(JournalRecord.MAX_DETAIL_CHARS))
        }
    }

    private fun readRecord(input: DataInputStream): JournalRecord {
        val time = input.readLong()
        val level = JournalLevel.valueOf(input.readUTF())
        val source = input.readUTF()
        val type = input.readUTF()
        val message = input.readUTF()
        val details = LinkedHashMap<String, String>()
        repeat(input.readUnsignedShort()) { details[input.readUTF()] = input.readUTF() }
        return JournalRecord(time, level, source, type, message, details)
    }

    private const val MAX_DETAILS = 64
}
