package org.pnfolder.pnlibrary.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.network.message.MessageCodec;
import org.pnfolder.pnlibrary.api.network.message.MessageReader;
import org.pnfolder.pnlibrary.api.network.message.MessageWriter;

class CompactCodecTest {
    static final class Row {
        final UUID id;
        final String name;
        final float x;
        final float y;
        final float z;
        final int level;
        final boolean op;

        Row(UUID id, String name, float x, float y, float z, int level, boolean op) {
            this.id = id; this.name = name; this.x = x; this.y = y; this.z = z; this.level = level; this.op = op;
        }
    }

    /** Level and the op flag share one number: level * 2 + (op ? 1 : 0). */
    static final class CompactRows implements MessageCodec<List<Row>> {
        @Override
        public void encode(List<Row> rows, MessageWriter out) {
            out.writeVarInt(rows.size());
            for (Row r : rows) {
                out.writeUuid(r.id).writeText(r.name).writeFloat(r.x).writeFloat(r.y).writeFloat(r.z)
                        .writeVarInt(r.level * 2 + (r.op ? 1 : 0));
            }
        }

        @Override
        public List<Row> decode(MessageReader in) {
            int count = in.readVarInt();
            List<Row> rows = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                UUID id = in.readUuid();
                String name = in.readText();
                float x = in.readFloat();
                float y = in.readFloat();
                float z = in.readFloat();
                int packed = in.readVarInt();
                rows.add(new Row(id, name, x, y, z, packed >> 1, (packed & 1) == 1));
            }
            return rows;
        }
    }

    private static List<Row> rows(int count) {
        Random random = new Random(7);
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(new Row(new UUID(random.nextLong(), random.nextLong()), "Player" + (1000000 + i), random.nextInt(2000) - 1000,
                    random.nextInt(120), random.nextInt(2000) - 1000, random.nextInt(60), random.nextInt(10) == 0));
        }
        return rows;
    }

    @Test
    void compactRowsRoundTripAndAreSmaller() {
        List<Row> rows = rows(520);
        byte[] bytes = new CompactRows().toBytes(rows);
        List<Row> back = new CompactRows().fromBytes(bytes);
        assertEquals(520, back.size());
        assertEquals(rows.get(300).id, back.get(300).id);
        assertEquals(rows.get(300).level, back.get(300).level);
        assertEquals(rows.get(300).op, back.get(300).op);
        assertTrue(bytes.length < 520 * 63, "was " + bytes.length);
        System.out.println("SIZE compact 520 rows = " + bytes.length + ", per row " + (bytes.length / 520.0));
    }

    @Test
    void compressedCodecRoundTripsAndIsSmallerStill() {
        MessageCodec<List<Row>> packed = MessageCodec.compressed(new CompactRows());
        List<Row> rows = rows(520);
        byte[] bytes = packed.toBytes(rows);
        assertEquals(520, packed.fromBytes(bytes).size());
        assertTrue(bytes.length < new CompactRows().toBytes(rows).length);
        System.out.println("SIZE compressed 520 rows = " + bytes.length);
    }

    @Test
    void aTinyMessageIsKeptAsItIsAndStillRoundTrips() {
        MessageCodec<String> text = new MessageCodec<String>() {
            @Override public void encode(String m, MessageWriter out) { out.writeText(m); }
            @Override public String decode(MessageReader in) { return in.readText(); }
        };
        MessageCodec<String> packed = MessageCodec.compressed(text);
        assertEquals("Steve", packed.fromBytes(packed.toBytes("Steve")));
        assertTrue(packed.toBytes("Steve").length <= text.toBytes("Steve").length + 5);
    }

    /** One row on its own, so that {@code split} can measure it. */
    static final class OneRow implements MessageCodec<Row> {
        @Override
        public void encode(Row r, MessageWriter out) {
            out.writeUuid(r.id).writeText(r.name).writeFloat(r.x).writeFloat(r.y).writeFloat(r.z).writeVarInt(r.level * 2 + (r.op ? 1 : 0));
        }

        @Override
        public Row decode(MessageReader in) {
            UUID id = in.readUuid();
            String name = in.readText();
            float x = in.readFloat();
            float y = in.readFloat();
            float z = in.readFloat();
            int packed = in.readVarInt();
            return new Row(id, name, x, y, z, packed >> 1, (packed & 1) == 1);
        }
    }

    @Test
    void aListCodecRoundTripsAndSplitKeepsEveryPartInsideTheLimit() {
        OneRow one = new OneRow();
        MessageCodec<List<Row>> list = MessageCodec.listOf(one);
        List<Row> rows = rows(2000);
        List<List<Row>> parts = one.split(rows);
        assertTrue(parts.size() > 1);
        List<Row> joined = new ArrayList<>();
        for (List<Row> part : parts) {
            byte[] bytes = list.toBytes(part);
            assertTrue(bytes.length <= 32766, "part was " + bytes.length);
            joined.addAll(list.fromBytes(bytes));
        }
        assertEquals(2000, joined.size());
        assertEquals(rows.get(1999).id, joined.get(1999).id);
        assertEquals(1, one.split(rows(3)).size());
        assertEquals(1, one.split(new ArrayList<Row>()).size());
    }

    @Test
    void aForgedListLengthFailsWithoutReservingMemory() {
        byte[] forged = new MessageCodec<Integer>() {
            @Override public void encode(Integer m, MessageWriter out) { out.writeVarInt(Integer.MAX_VALUE); }
            @Override public Integer decode(MessageReader in) { return 0; }
        }.toBytes(0);
        assertThrows(IllegalArgumentException.class, () -> MessageCodec.listOf(new OneRow()).fromBytes(forged));
    }

    @Test
    void aForgedSizeIsRefusedAndNumbersKeepTheirSign() {
        MessageCodec<List<Row>> packed = MessageCodec.compressed(new CompactRows());
        byte[] forged = new MessageCodec<Integer>() {
            @Override public void encode(Integer m, MessageWriter out) { out.writeByte(1).writeVarInt(Integer.MAX_VALUE).writeBlock(new byte[1]); }
            @Override public Integer decode(MessageReader in) { return 0; }
        }.toBytes(0);
        assertThrows(IllegalArgumentException.class, () -> packed.fromBytes(forged));
        MessageCodec<Integer> varint = new MessageCodec<Integer>() {
            @Override public void encode(Integer m, MessageWriter out) { out.writeVarInt(m).writeByte(-3).writeShort(-300); }
            @Override public Integer decode(MessageReader in) { return in.readVarInt() + in.readByte() + in.readShort(); }
        };
        assertEquals(300 - 3 - 300, varint.fromBytes(varint.toBytes(300)));
    }
}
