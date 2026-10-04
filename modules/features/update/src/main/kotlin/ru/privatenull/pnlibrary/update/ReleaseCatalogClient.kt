package ru.privatenull.pnlibrary.update

import java.net.URI
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/** Loads one manually maintained releases.json and caches only valid snapshots. */
class ReleaseCatalogClient(
    private val http: TrustedHttpClient,
    private val store: ReleaseCatalogueStore,
    private val executor: Executor,
    private val ttl: Duration,
    private val codec: ReleaseCatalogCodec = ReleaseCatalogCodec(),
) {
    /** Loads [source], optionally forcing remote refresh, and caches valid bytes. */
    fun load(source: URI, refresh: RefreshMode = RefreshMode.CACHED): CompletableFuture<ReleaseCatalog> =
        CompletableFuture.supplyAsync({
            val cached = store.read(source, ttl)
            if (refresh == RefreshMode.CACHED && cached?.fresh == true) return@supplyAsync codec.decode(cached.bytes)
            val bytes = try {
                http.get(source, ReleaseCatalogCodec.MAX_BYTES)
            } catch (error: Throwable) {
                if (cached != null && refresh == RefreshMode.CACHED) return@supplyAsync codec.decode(cached.bytes)
                throw error
            }
            val catalog = codec.decode(bytes)
            store.write(source, bytes)
            catalog
        }, executor)
}
