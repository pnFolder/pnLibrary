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
 * All endpoint probes run concurrently. The caller itself does not occupy the
 * request executor, preventing the nested-executor starvation that previously
 * made update checks wait forever when several products were registered.
 */
internal class ReleaseCatalogGateway(
    private val client: ReleaseCatalogClient,
    private val http: TrustedHttpClient,
    private val requestExecutor: Executor,
) {
    fun load(source: CatalogSource, refreshMode: RefreshMode): ReleaseCatalog {
        val endpoints = CatalogEndpoints(source)
        val result = CompletableFuture<ReleaseCatalog>()
        val failures = Collections.synchronizedList(mutableListOf<String>())
        val remaining = AtomicInteger(ENDPOINT_COUNT)

        submit("raw", result, failures, remaining, endpoints) {
            client.load(endpoints.raw, refreshMode).join()
        }
        submit("api", result, failures, remaining, endpoints) {
            loadFromContentsApi(endpoints.api)
        }
        submit("jsDelivr", result, failures, remaining, endpoints) {
            client.load(endpoints.cdn, RefreshMode.FORCE_REMOTE).join()
        }

        return result.join()
    }

    private fun submit(
        label: String,
        result: CompletableFuture<ReleaseCatalog>,
        failures: MutableList<String>,
        remaining: AtomicInteger,
        endpoints: CatalogEndpoints,
        request: () -> ReleaseCatalog,
    ) {
        CompletableFuture.supplyAsync(request, requestExecutor)
            .whenComplete { catalog, error ->
                if (error == null) {
                    result.complete(catalog)
                    return@whenComplete
                }

                failures += "$label: ${failureMessage(error)}"
                if (remaining.decrementAndGet() == 0) {
                    result.completeExceptionally(
                        IllegalStateException(
                            "Release catalog not found at ${endpoints.raw}; check the repository branch " +
                                "(PluginUpdateRequest.Builder.branch). " +
                                "Tried GitHub raw, Contents API and jsDelivr: " + failures.joinToString("; "),
                            error.cause ?: error,
                        ),
                    )
                }
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
        const val ENDPOINT_COUNT = 3
    }
}
