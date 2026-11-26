package org.pnfolder.pnlibrary.core.link;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.core.link.remote.DataCall;
import org.pnfolder.pnlibrary.core.link.remote.DataChunk;
import org.pnfolder.pnlibrary.core.link.remote.DataHandler;
import org.pnfolder.pnlibrary.core.link.remote.LinkNames;
import org.pnfolder.pnlibrary.core.link.remote.RemoteExecutor;
import org.pnfolder.pnlibrary.core.link.remote.RemoteRequest;
import org.pnfolder.pnlibrary.core.link.remote.RemoteResult;
import org.pnfolder.pnlibrary.core.link.wire.CallFrame;
import org.pnfolder.pnlibrary.core.link.wire.ChunkFrame;
import org.pnfolder.pnlibrary.core.link.wire.DataChunks;
import org.pnfolder.pnlibrary.core.link.wire.LinkCodec;
import org.pnfolder.pnlibrary.core.link.wire.LinkFrame;
import org.pnfolder.pnlibrary.core.link.wire.ReportFrame;
import org.pnfolder.pnlibrary.core.link.wire.RequestFrame;
import org.pnfolder.pnlibrary.core.link.wire.ResultFrame;

/**
 * The proxy end of the link: takes a plugin message that arrived from a game server.
 *
 * <p>The platform listener tells it whether the sender was a server connection or a player; a message from a player
 * is dropped before its signature is even looked at, and a message without the right signature is dropped too. A valid
 * message is a report (remembered), a request for another server (passed on, or carried out here when the proxy is
 * the target) or the answer to a request (passed on to whoever asked).
 */
public final class LinkReceiver {
    private final Link link;
    private final RemoteExecutor executor;
    private final DataHandler data;

    public LinkReceiver(@NotNull Link link) {
        this(link, null, null);
    }

    /**
     * @param link the proxy's link; without a key nothing is accepted
     * @param executor carries out requests addressed to the proxy
     * @param data answers the menu questions addressed to the proxy
     */
    public LinkReceiver(@NotNull Link link, @Nullable RemoteExecutor executor, @Nullable DataHandler data) {
        this.link = link;
        this.executor = executor;
        this.data = data;
    }

    /** Whether the link has a key; without one nothing is accepted. */
    public boolean getEnabled() {
        return link.getEnabled();
    }

    /** Handles {@code message} from {@code server}; returns true when it was a valid message. */
    public boolean receive(@NotNull String server, boolean fromServer, @NotNull byte[] message) {
        byte[] key = link.getKey();
        if (!fromServer || key == null) {
            return false;
        }
        LinkFrame frame = LinkCodec.decodeFrame(message, key);
        if (frame == null) {
            return false;
        }
        if (frame instanceof ReportFrame) {
            link.getRegistry().accept(server, ((ReportFrame) frame).getReport());
        } else if (frame instanceof RequestFrame) {
            route(server, ((RequestFrame) frame).getRequest().withOrigin(server), key);
        } else if (frame instanceof ResultFrame) {
            deliver(((ResultFrame) frame).getResult(), key);
        } else if (frame instanceof CallFrame) {
            routeCall(server, ((CallFrame) frame).getCall().withOrigin(server), key);
        } else if (frame instanceof ChunkFrame) {
            DataChunk chunk = ((ChunkFrame) frame).getChunk();
            if (chunk.getTo().equals(LinkNames.PROXY_ORIGIN)) {
                link.getData().accept(chunk);
            } else {
                link.send(chunk.getTo(), message);
            }
        }
        return true;
    }

    private void route(String server, RemoteRequest request, byte[] key) {
        Consumer<RemoteResult> toOrigin = result -> link.send(server, LinkCodec.encodeResult(result, key));
        if (request.getTarget().equalsIgnoreCase(LinkNames.PROXY)) {
            if (executor != null) {
                executor.run(request, toOrigin);
            } else {
                toOrigin.accept(new RemoteResult(request.getId(), server, false, "Прокси не принимает действия.", Instant.now()));
            }
            return;
        }
        if (!link.send(request.getTarget(), LinkCodec.encodeRequest(request, key))) {
            toOrigin.accept(new RemoteResult(request.getId(), server, false,
                "Сервер " + request.getTarget() + " недоступен.", Instant.now()));
        }
    }

    /** Passes a question on to the server it is for, answers it here when it is for the proxy, or tells the asker why it cannot. */
    private void routeCall(String server, DataCall call, byte[] key) {
        if (call.getTarget().equalsIgnoreCase(LinkNames.PROXY)) {
            if (data == null) {
                refuse(server, call, key, "Прокси не отвечает на запросы меню.");
            } else {
                data.handle(call, (ok, text) -> answer(server, call.getId(), ok, text, key));
            }
            return;
        }
        if (!link.send(call.getTarget(), LinkCodec.encodeCall(call, key))) {
            refuse(server, call, key, "Сервер " + call.getTarget() + " недоступен.");
        }
    }

    private void refuse(String server, DataCall call, byte[] key, String text) {
        answer(server, call.getId(), false, text, key);
    }

    private void answer(String server, UUID id, boolean ok, String text, byte[] key) {
        List<byte[]> pieces = DataChunks.split(id, server, ok, text, key);
        if (pieces == null) {
            pieces = DataChunks.split(id, server, false, "Ответ слишком большой.", key);
        }
        for (byte[] piece : pieces == null ? Collections.<byte[]>emptyList() : pieces) {
            link.send(server, piece);
        }
    }

    private void deliver(RemoteResult result, byte[] key) {
        if (result.getOrigin().equals(LinkNames.PROXY_ORIGIN)) {
            link.getReplies().complete(result);
        } else {
            link.send(result.getOrigin(), LinkCodec.encodeResult(result, key));
        }
    }
}
