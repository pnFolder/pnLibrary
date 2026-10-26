package org.pnfolder.pnlibrary.core.journal

import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
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
        visit(ByteArrayInputStream(bytes), bytes.size.toLong()) { result += it }
        return result
    }

    /** Streams intact blocks under a byte budget, retaining one compressed block at a time. */
    fun visit(stream: InputStream, maximumBytes: Long, record: (JournalRecord) -> Unit) {
        val input = DataInputStream(stream)
        var remaining = maximumBytes
        while (remaining >= HEADER_BYTES) {
            val length: Int
            val expectedCrc: Long
            try {
                length = input.readInt()
                expectedCrc = input.readInt().toLong() and 0xFFFF_FFFFL
            } catch (_: EOFException) { break }
            remaining -= HEADER_BYTES
            if (length < 0 || length > MAX_PAYLOAD_BYTES) break
            if (length > remaining) break
            val payload = ByteArray(length)
            try { input.readFully(payload) } catch (_: EOFException) { break }
            remaining -= length
            if (CRC32().apply { update(payload) }.value != expectedCrc) continue
            runCatchingNonFatal { decodePayload(payload) }.getOrElse { emptyList() }.forEach(record)
        }
    }

    private fun decodePayload(payload: ByteArray): List<JournalRecord> =
        DataInputStream(GZIPInputStream(ByteArrayInputStream(payload))).use { input ->
            val count = input.readInt()
            require(count in 0..10_000) { "Invalid journal record count" }
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
            val limit = if (key == JournalRecord.STACKTRACE) JournalRecord.MAX_STACK_CHARS else JournalRecord.MAX_DETAIL_CHARS
            out.writeUTF(value.take(limit))
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
