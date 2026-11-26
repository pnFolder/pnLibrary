package org.pnfolder.pnlibrary.core.link.remote;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.core.link.LinkTimeouts;
import org.pnfolder.pnlibrary.core.link.wire.DataChunks;
import org.pnfolder.pnlibrary.core.link.wire.LinkCodec;

/** Asks other servers data questions over the link and collects the pieces of their answers. */
public final class DataClient {
    private static final long TIMEOUT_SECONDS = 20L;

    private static final class Pending {
        final CompletableFuture<String> future;
        final ConcurrentHashMap<Integer, byte[]> pieces = new ConcurrentHashMap<Integer, byte[]>();
        volatile ScheduledFuture<?> timeout;

        Pending(CompletableFuture<String> future) {
            this.future = future;
        }
    }

    private final byte[] key;
    private final long timeoutSeconds;
    private final Predicate<byte[]> uplink;
    private final ConcurrentHashMap<UUID, Pending> waiting = new ConcurrentHashMap<UUID, Pending>();
    private final LinkTimeouts timeouts = new LinkTimeouts("pnlibrary-link-data");

    public DataClient(@Nullable byte[] key, @NotNull Predicate<byte[]> uplink) {
        this(key, TIMEOUT_SECONDS, uplink);
    }

    /**
     * @param key the shared link key, or null when the link is off
     * @param timeoutSeconds how long to wait for the answer
     * @param uplink sends the signed question; false when there is nobody to carry it
     */
    public DataClient(@Nullable byte[] key, long timeoutSeconds, @NotNull Predicate<byte[]> uplink) {
        this.key = key;
        this.timeoutSeconds = timeoutSeconds;
        this.uplink = uplink;
    }

    public @NotNull CompletableFuture<String> call(@NotNull String target, @NotNull String viewer, @NotNull String method,
                                                   @NotNull String args) {
        CompletableFuture<String> future = new CompletableFuture<String>();
        if (key == null) {
            future.completeExceptionally(new DataException("Связь выключена: нет файла link.key."));
            return future;
        }
        DataCall call = new DataCall(UUID.randomUUID(), target, "", viewer, method, args, Instant.now());
        byte[] bytes = LinkCodec.encodeCall(call, key);
        if (bytes.length > LinkCodec.MAX_FRAME_BYTES) {
            future.completeExceptionally(new DataException("Запрос слишком длинный."));
            return future;
        }
        Pending pending = new Pending(future);
        waiting.put(call.getId(), pending);
        if (!uplink.test(bytes)) {
            waiting.remove(call.getId());
            future.completeExceptionally(new DataException("Некому передать запрос: на этом сервере нет игроков онлайн."));
            return future;
        }
        pending.timeout = timeouts.after(timeoutSeconds, () -> {
            if (waiting.remove(call.getId(), pending)) {
                fail(pending, "Сервер " + target + " не ответил за " + timeoutSeconds + " секунд.");
            }
        });
        return future;
    }

    /** Takes one piece of an answer; completes the question when all pieces are in. */
    public void accept(@NotNull DataChunk chunk) {
        Pending pending = waiting.get(chunk.getId());
        if (pending == null) {
            return;
        }
        pending.pieces.put(chunk.getIndex(), chunk.getBytes());
        if (pending.pieces.size() < chunk.getCount() || !waiting.remove(chunk.getId(), pending)) {
            return;
        }
        if (pending.timeout != null) {
            pending.timeout.cancel(false);
        }
        List<byte[]> pieces = new ArrayList<byte[]>();
        for (int i = 0; i < chunk.getCount(); i++) {
            byte[] piece = pending.pieces.get(i);
            if (piece == null) {
                fail(pending, "Ответ неполный.");
                return;
            }
            pieces.add(piece);
        }
        String text = DataChunks.join(pieces);
        if (text == null) {
            fail(pending, "Ответ не удалось прочитать.");
        } else if (chunk.getOk()) {
            pending.future.complete(text);
        } else {
            fail(pending, text);
        }
    }

    private static void fail(Pending pending, String text) {
        pending.future.completeExceptionally(new DataException(text));
    }
}
