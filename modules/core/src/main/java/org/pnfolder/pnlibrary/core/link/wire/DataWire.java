package org.pnfolder.pnlibrary.core.link.wire;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.pnfolder.pnlibrary.core.link.remote.DataCall;
import org.pnfolder.pnlibrary.core.link.remote.DataChunk;

/** The byte layout of data questions and the pieces of their answers. */
final class DataWire {
    private DataWire() {
    }

    static void writeCall(DataOutputStream out, DataCall call) throws IOException {
        out.writeLong(call.getId().getMostSignificantBits());
        out.writeLong(call.getId().getLeastSignificantBits());
        out.writeLong(call.getSentAt().toEpochMilli());
        out.writeUTF(WireText.take(call.getTarget(), LinkCodec.MAX_TEXT));
        out.writeUTF(WireText.take(call.getOrigin(), LinkCodec.MAX_TEXT));
        out.writeUTF(WireText.take(call.getViewer(), LinkCodec.MAX_TEXT));
        out.writeUTF(WireText.take(call.getMethod(), LinkCodec.MAX_TEXT));
        byte[] args = call.getArgs().getBytes(StandardCharsets.UTF_8);
        out.writeInt(args.length);
        out.write(args);
    }

    static DataCall readCall(DataInputStream input, Instant now) throws IOException {
        UUID id = new UUID(input.readLong(), input.readLong());
        Instant sentAt = Instant.ofEpochMilli(input.readLong());
        if (!LinkCodec.fresh(sentAt, now)) {
            return null;
        }
        String target = input.readUTF();
        String origin = input.readUTF();
        String viewer = input.readUTF();
        String method = input.readUTF();
        byte[] args = new byte[Math.max(0, Math.min(input.readInt(), LinkCodec.MAX_FRAME_BYTES))];
        input.readFully(args);
        return new DataCall(id, target, origin, viewer, method, new String(args, StandardCharsets.UTF_8), sentAt);
    }

    static void writeChunk(DataOutputStream out, DataChunk chunk) throws IOException {
        out.writeLong(chunk.getId().getMostSignificantBits());
        out.writeLong(chunk.getId().getLeastSignificantBits());
        out.writeLong(chunk.getSentAt().toEpochMilli());
        out.writeUTF(WireText.take(chunk.getTo(), LinkCodec.MAX_TEXT));
        out.writeBoolean(chunk.getOk());
        out.writeInt(chunk.getIndex());
        out.writeInt(chunk.getCount());
        out.writeInt(chunk.getBytes().length);
        out.write(chunk.getBytes());
    }

    static DataChunk readChunk(DataInputStream input, Instant now) throws IOException {
        UUID id = new UUID(input.readLong(), input.readLong());
        Instant sentAt = Instant.ofEpochMilli(input.readLong());
        if (!LinkCodec.fresh(sentAt, now)) {
            return null;
        }
        String to = input.readUTF();
        boolean ok = input.readBoolean();
        int index = input.readInt();
        int count = input.readInt();
        if (count < 1 || count > DataChunks.MAX_PIECES || index < 0 || index >= count) {
            return null;
        }
        byte[] bytes = new byte[Math.max(0, Math.min(input.readInt(), DataChunks.PIECE))];
        input.readFully(bytes);
        return new DataChunk(id, to, ok, index, count, bytes, sentAt);
    }
}
