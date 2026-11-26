package org.pnfolder.pnlibrary.core.journal.store;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.journal.JournalLevel;
import org.pnfolder.pnlibrary.core.journal.JournalRecord;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Reads and writes journal blocks. A file is a sequence of blocks with no file header, so any prefix stays readable:
 *
 * <pre>
 * block   := u32 payloadLength | u32 crc32(payload) | payload
 * payload := gzip( u32 recordCount, record* )
 * record  := i64 timeMillis | utf level | utf source | utf type | utf message | u16 detailCount | (utf key | utf value)*
 * </pre>
 *
 * A damaged block is skipped by its length; a truncated tail (power loss mid-write) is ignored, so earlier blocks
 * always survive.
 */
public final class JournalBlockCodec {
    private static final int HEADER_BYTES = 8;
    private static final int MAX_PAYLOAD_BYTES = 16 * 1024 * 1024;
    private static final int MAX_DETAILS = 64;

    private JournalBlockCodec() {
    }

    /** Packs {@code records} into one block; throws {@link IllegalArgumentException} when the block is too large. */
    @NotNull
    public static byte[] encode(@NotNull List<JournalRecord> records) {
        byte[] payload = compress(records);
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Journal block is too large: " + payload.length + " bytes");
        }
        CRC32 crc = new CRC32();
        crc.update(payload);
        ByteArrayOutputStream block = new ByteArrayOutputStream(HEADER_BYTES + payload.length);
        try (DataOutputStream out = new DataOutputStream(block)) {
            out.writeInt(payload.length);
            out.writeInt((int) crc.getValue());
            out.write(payload);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return block.toByteArray();
    }

    private static byte[] compress(List<JournalRecord> records) {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(raw); DataOutputStream out = new DataOutputStream(gzip)) {
            out.writeInt(records.size());
            for (JournalRecord record : records) {
                writeRecord(out, record);
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return raw.toByteArray();
    }

    /** Decodes every complete block; stops at the first incomplete one, which is what an interrupted write leaves. */
    public static @NotNull List<JournalRecord> decodeAll(@NotNull byte[] bytes) {
        List<JournalRecord> result = new ArrayList<>();
        visit(new ByteArrayInputStream(bytes), bytes.length, result::add);
        return result;
    }

    /** Retains one compressed block at a time; blocks with a bad checksum are skipped. */
    public static void visit(@NotNull InputStream stream, long maximumBytes, @NotNull Consumer<JournalRecord> record) {
        DataInputStream input = new DataInputStream(stream);
        long remaining = maximumBytes;
        try {
            while (remaining >= HEADER_BYTES) {
                int length;
                long expectedCrc;
                try {
                    length = input.readInt();
                    expectedCrc = input.readInt() & 0xFFFF_FFFFL;
                } catch (EOFException end) {
                    break;
                }
                remaining -= HEADER_BYTES;
                if (length < 0 || length > MAX_PAYLOAD_BYTES || length > remaining) {
                    break;
                }
                byte[] payload = new byte[length];
                try {
                    input.readFully(payload);
                } catch (EOFException end) {
                    break;
                }
                remaining -= length;
                CRC32 crc = new CRC32();
                crc.update(payload);
                if (crc.getValue() != expectedCrc) {
                    continue;
                }
                for (JournalRecord decoded : Failures.runCatchingNonFatal(() -> decodePayload(payload))
                    .getOrDefault(Collections.emptyList())) {
                    record.accept(decoded);
                }
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static List<JournalRecord> decodePayload(byte[] payload) throws IOException {
        try (DataInputStream input = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(payload)))) {
            int count = input.readInt();
            if (count < 0 || count > 10_000) {
                throw new IllegalArgumentException("Invalid journal record count");
            }
            List<JournalRecord> records = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                records.add(readRecord(input));
            }
            return records;
        }
    }

    private static void writeRecord(DataOutputStream out, JournalRecord record) throws IOException {
        out.writeLong(record.getTimeMillis());
        out.writeUTF(record.getLevel().name());
        out.writeUTF(take(record.getSource(), JournalRecord.MAX_DETAIL_CHARS));
        out.writeUTF(take(record.getType(), JournalRecord.MAX_DETAIL_CHARS));
        out.writeUTF(take(record.getMessage(), JournalRecord.MAX_MESSAGE_CHARS));
        List<Map.Entry<String, String>> details = new ArrayList<>();
        for (Map.Entry<String, String> entry : record.getDetails().entrySet()) {
            if (details.size() == MAX_DETAILS) {
                break;
            }
            details.add(entry);
        }
        out.writeShort(details.size());
        for (Map.Entry<String, String> entry : details) {
            out.writeUTF(take(entry.getKey(), JournalRecord.MAX_DETAIL_CHARS));
            int limit = JournalRecord.STACKTRACE.equals(entry.getKey())
                ? JournalRecord.MAX_STACK_CHARS : JournalRecord.MAX_DETAIL_CHARS;
            out.writeUTF(take(entry.getValue(), limit));
        }
    }

    private static JournalRecord readRecord(DataInputStream input) throws IOException {
        long time = input.readLong();
        JournalLevel level = JournalLevel.valueOf(input.readUTF());
        String source = input.readUTF();
        String type = input.readUTF();
        String message = input.readUTF();
        Map<String, String> details = new LinkedHashMap<>();
        int count = input.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            String key = input.readUTF();
            details.put(key, input.readUTF());
        }
        return new JournalRecord(time, level, source, type, message, details);
    }

    private static String take(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
