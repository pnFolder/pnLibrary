package org.pnfolder.pnlibrary.core.link.wire;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.pnfolder.pnlibrary.core.link.remote.RemoteAction;
import org.pnfolder.pnlibrary.core.link.remote.RemoteRequest;
import org.pnfolder.pnlibrary.core.link.remote.RemoteResult;

/** The byte layout of update requests and their answers. */
final class RemoteWire {
    private static final int MAX_RESULT_TEXT = 1_000;

    private RemoteWire() {
    }

    static void writeRequest(DataOutputStream out, RemoteRequest request) throws IOException {
        out.writeLong(request.getId().getMostSignificantBits());
        out.writeLong(request.getId().getLeastSignificantBits());
        out.writeLong(request.getSentAt().toEpochMilli());
        out.writeUTF(WireText.take(request.getTarget(), LinkCodec.MAX_TEXT));
        out.writeUTF(WireText.take(request.getOrigin(), LinkCodec.MAX_TEXT));
        out.writeUTF(WireText.take(request.getActor(), LinkCodec.MAX_TEXT));
        out.writeUTF(request.getAction().name());
        out.writeUTF(WireText.take(request.getProduct() == null ? "" : request.getProduct(), LinkCodec.MAX_TEXT));
    }

    static RemoteRequest readRequest(DataInputStream input, Instant now) throws IOException {
        UUID id = new UUID(input.readLong(), input.readLong());
        Instant sentAt = Instant.ofEpochMilli(input.readLong());
        if (!LinkCodec.fresh(sentAt, now)) {
            return null;
        }
        String target = input.readUTF();
        String origin = input.readUTF();
        String actor = input.readUTF();
        String actionName = input.readUTF();
        RemoteAction action = null;
        for (RemoteAction each : RemoteAction.values()) {
            if (each.name().equals(actionName)) {
                action = each;
            }
        }
        if (action == null) {
            return null;
        }
        String product = input.readUTF();
        return new RemoteRequest(id, target, origin, actor, action, product.isEmpty() ? null : product, sentAt);
    }

    static void writeResult(DataOutputStream out, RemoteResult result) throws IOException {
        out.writeLong(result.getId().getMostSignificantBits());
        out.writeLong(result.getId().getLeastSignificantBits());
        out.writeLong(result.getSentAt().toEpochMilli());
        out.writeUTF(WireText.take(result.getOrigin(), LinkCodec.MAX_TEXT));
        out.writeBoolean(result.getOk());
        out.writeUTF(WireText.take(result.getText(), MAX_RESULT_TEXT));
    }

    static RemoteResult readResult(DataInputStream input, Instant now) throws IOException {
        UUID id = new UUID(input.readLong(), input.readLong());
        Instant sentAt = Instant.ofEpochMilli(input.readLong());
        if (!LinkCodec.fresh(sentAt, now)) {
            return null;
        }
        String origin = input.readUTF();
        boolean ok = input.readBoolean();
        return new RemoteResult(id, origin, ok, input.readUTF(), sentAt);
    }
}
