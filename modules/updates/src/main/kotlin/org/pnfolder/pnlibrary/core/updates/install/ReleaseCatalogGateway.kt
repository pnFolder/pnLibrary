package org.pnfolder.pnlibrary.core.updates.install

import com.google.gson.JsonParser
import org.pnfolder.pnlibrary.api.updates.CatalogSource
import org.pnfolder.pnlibrary.core.updates.engine.RefreshMode
import org.pnfolder.pnlibrary.core.updates.engine.ReleaseCatalog
import org.pnfolder.pnlibrary.core.updates.engine.ReleaseCatalogClient
import org.pnfolder.pnlibrary.core.updates.engine.ReleaseCatalogCodec
import org.pnfolder.pnlibrary.core.updates.engine.TrustedHttpClient
import java.net.URI
import java.util.Base64
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

/**
 * Loads one release catalog through independent GitHub endpoints.
 *
 * The GitHub endpoints run concurrently; jsDelivr is the last resort. The caller itself does not occupy the
 * request executor, preventing the nested-executor starvation that previously
 * made update checks wait forever when several products were registered.
 */
internal class ReleaseCatalogGateway(
    private val client: ReleaseCatalogClient,
    private val http: TrustedHttpClient,
    private val requestExecutor: Executor,
) {
    /**
     * Returns the first catalog GitHub raw or the Contents API delivers. jsDelivr caches for hours and may
     * serve a stale catalog, so it is asked only when both GitHub endpoints failed.
     */
    fun load(source: CatalogSource, refreshMode: RefreshMode): ReleaseCatalog {
        val endpoints = CatalogEndpoints(source)
        val result = CompletableFuture<ReleaseCatalog>()
        val failures = Collections.synchronizedList(mutableListOf<String>())
        val remaining = AtomicInteger(PRIMARY_ENDPOINT_COUNT)

        val fallback = {
            submit("jsDelivr", result, failures, endpoints = endpoints) { client.load(endpoints.cdn, RefreshMode.FORCE_REMOTE).join() }
        }
        submit("raw", result, failures, { onAllFailed(remaining, fallback) }) {
            client.load(endpoints.raw, refreshMode).join()
        }
        submit("api", result, failures, { onAllFailed(remaining, fallback) }) {
            loadFromContentsApi(endpoints.api)
        }
        return result.join()
    }

    private fun onAllFailed(remaining: AtomicInteger, fallback: () -> Unit) {
        if (remaining.decrementAndGet() == 0) fallback()
    }

    private fun submit(
        label: String,
        result: CompletableFuture<ReleaseCatalog>,
        failures: MutableList<String>,
        onFailure: (() -> Unit)? = null,
        endpoints: CatalogEndpoints? = null,
        request: () -> ReleaseCatalog,
    ) {
        CompletableFuture.supplyAsync(request, requestExecutor)
            .whenComplete { catalog, error ->
                if (error == null) {
                    result.complete(catalog)
                    return@whenComplete
                }

                failures += "$label: ${failureMessage(error)}"
                if (onFailure != null) {
                    onFailure()
                    return@whenComplete
                }
                result.completeExceptionally(
                    IllegalStateException(
                        "Release catalog not found at ${endpoints?.raw}; check the repository branch " +
                            "(PluginUpdateRequest.Builder.branch). " +
                            "Tried GitHub raw, Contents API and jsDelivr: " + failures.joinToString("; "),
                        error.cause ?: error,
                    ),
                )
            }
    }

    private fun loadFromContentsApi(source: URI): ReleaseCatalog {
        val response = http.get(source, ReleaseCatalogCodec.MAX_BYTES * 2)
        val content = JsonParser.parseString(response.toString(Charsets.UTF_8))
            .asJsonObject
            .get("content")
            ?.asString
            ?: error("GitHub API response does not contain file content")
        return ReleaseCatalogCodec().decode(Base64.getMimeDecoder().decode(content))
    }

    private fun failureMessage(error: Throwable): String =
        error.cause?.message ?: error.message ?: error.javaClass.simpleName

    private data class CatalogEndpoints(
        val raw: URI,
        val api: URI,
        val cdn: URI,
    ) {
        constructor(source: CatalogSource) : this(
            raw = URI.create(source.rawUrl),
            api = URI.create(source.apiUrl),
            cdn = URI.create(source.cdnUrl),
        )
    }

    private companion object {
        /** GitHub endpoints queried in parallel before jsDelivr is tried. */
        const val PRIMARY_ENDPOINT_COUNT = 2
    }
}
