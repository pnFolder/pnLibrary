package org.pnfolder.pnlibrary.core.link.remote;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.link.LinkTimeouts;

/** The answers still awaited for requests this server made. */
public final class RemoteReplies {
    private static final long TIMEOUT_SECONDS = 20L;

    private static final class Waiting {
        final Consumer<RemoteResult> onResult;
        volatile ScheduledFuture<?> timeout;

        Waiting(Consumer<RemoteResult> onResult) {
            this.onResult = onResult;
        }
    }

    private final ConcurrentHashMap<UUID, Waiting> waiting = new ConcurrentHashMap<>();
    private final LinkTimeouts timeouts = new LinkTimeouts("pnlibrary-link-replies");

    /** Waits for the answer to request {@code id}; if none comes in time, {@code onResult} gets a failure instead. */
    public void expect(@NotNull UUID id, @NotNull Consumer<RemoteResult> onResult) {
        Waiting entry = new Waiting(onResult);
        waiting.put(id, entry);
        entry.timeout = timeouts.after(TIMEOUT_SECONDS, () -> {
            if (waiting.remove(id, entry)) {
                onResult.accept(new RemoteResult(id, "", false, "Сервер не ответил за " + TIMEOUT_SECONDS + " секунд.",
                    Instant.now()));
            }
        });
    }

    /** Stops waiting for request {@code id}, for a request that could not be sent. */
    public void cancel(@NotNull UUID id) {
        Waiting entry = waiting.remove(id);
        if (entry != null && entry.timeout != null) {
            entry.timeout.cancel(false);
        }
    }

    /** Hands {@code result} to whoever waits for it; returns false when nobody does. */
    public boolean complete(@NotNull RemoteResult result) {
        Waiting entry = waiting.remove(result.getId());
        if (entry == null) {
            return false;
        }
        if (entry.timeout != null) {
            entry.timeout.cancel(false);
        }
        entry.onResult.accept(result);
        return true;
    }
}
