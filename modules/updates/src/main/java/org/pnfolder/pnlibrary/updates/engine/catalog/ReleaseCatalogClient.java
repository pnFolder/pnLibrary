package org.pnfolder.pnlibrary.updates.engine.catalog;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.updates.engine.net.TrustedHttpClient;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/** Loads one manually maintained releases.json and caches only valid snapshots. */
public final class ReleaseCatalogClient {
    private final TrustedHttpClient http;
    private final ReleaseCatalogStore store;
    private final Executor executor;
    private final Duration ttl;
    private final ReleaseCatalogCodec codec;

    public ReleaseCatalogClient(@NotNull TrustedHttpClient http, @NotNull ReleaseCatalogStore store,
                                @NotNull Executor executor, @NotNull Duration ttl) {
        this(http, store, executor, ttl, new ReleaseCatalogCodec());
    }

    public ReleaseCatalogClient(@NotNull TrustedHttpClient http, @NotNull ReleaseCatalogStore store,
                                @NotNull Executor executor, @NotNull Duration ttl, @NotNull ReleaseCatalogCodec codec) {
        this.http = http;
        this.store = store;
        this.executor = executor;
        this.ttl = ttl;
        this.codec = codec;
    }

    /** Loads {@code source} from the cache when it is fresh, caching valid bytes. */
    @NotNull
    public CompletableFuture<ReleaseCatalog> load(@NotNull URI source) {
        return load(source, RefreshMode.CACHED);
    }

    /** Loads {@code source}, optionally forcing remote refresh, and caches valid bytes. */
    @NotNull
    public CompletableFuture<ReleaseCatalog> load(@NotNull URI source, @NotNull RefreshMode refresh) {
        return CompletableFuture.supplyAsync(() -> loadNow(source, refresh), executor);
    }

    private ReleaseCatalog loadNow(URI source, RefreshMode refresh) {
        CatalogCacheHit cached = store.read(source, ttl);
        if (refresh == RefreshMode.CACHED && cached != null && cached.getFresh()) {
            return codec.decode(cached.getBytes());
        }
        byte[] bytes;
        try {
            bytes = http.get(source, ReleaseCatalogCodec.MAX_BYTES);
        } catch (Throwable error) {
            if (cached != null && refresh == RefreshMode.CACHED) {
                return codec.decode(cached.getBytes());
            }
            throw unchecked(error);
        }
        ReleaseCatalog catalog = codec.decode(bytes);
        try {
            store.write(source, bytes);
        } catch (IOException error) {
            throw new CompletionException(error);
        }
        return catalog;
    }

    // The future wraps a checked failure in CompletionException itself; this only keeps unchecked ones as they are.
    private static RuntimeException unchecked(Throwable error) {
        if (error instanceof RuntimeException) {
            return (RuntimeException) error;
        }
        if (error instanceof Error) {
            throw (Error) error;
        }
        return new CompletionException(error);
    }
}
