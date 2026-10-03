package ru.privatenull.pnlibrary.core.updates

import com.google.gson.JsonParser
import ru.privatenull.pnlibrary.update.RefreshMode
import ru.privatenull.pnlibrary.update.ReleaseCatalog
import ru.privatenull.pnlibrary.update.ReleaseCatalogClient
import ru.privatenull.pnlibrary.update.ReleaseCatalogCodec
import ru.privatenull.pnlibrary.update.TrustedHttpClient
import java.net.URI
import java.util.Base64
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
    fun load(
        owner: String,
        repository: String,
        refreshMode: RefreshMode,
    ): ReleaseCatalog {
        val endpoints = CatalogEndpoints(owner, repository)
        val result = CompletableFuture<ReleaseCatalog>()
        val failures = java.util.Collections.synchronizedList(mutableListOf<String>())
        val remaining = AtomicInteger(ENDPOINT_COUNT)

        submit("raw", result, failures, remaining) {
            client.load(endpoints.raw, refreshMode).join()
        }
        submit("api", result, failures, remaining) {
            loadFromContentsApi(endpoints.api)
        }
        submit("jsDelivr", result, failures, remaining) {
            client.load(endpoints.cdn, RefreshMode.FORCE_REMOTE).join()
        }

        return result.join()
    }

    private fun submit(
        label: String,
        result: CompletableFuture<ReleaseCatalog>,
        failures: MutableList<String>,
        remaining: AtomicInteger,
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
                            "Release catalog unavailable via GitHub raw, Contents API and jsDelivr: " +
                                failures.joinToString("; "),
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
        constructor(owner: String, repository: String) : this(
            raw = URI.create(
                "https://raw.githubusercontent.com/$owner/$repository/refs/heads/main/.pnlibrary/releases.json",
            ),
            api = URI.create(
                "https://api.github.com/repos/$owner/$repository/contents/.pnlibrary/releases.json?ref=main",
            ),
            cdn = URI.create(
                "https://cdn.jsdelivr.net/gh/$owner/$repository@main/.pnlibrary/releases.json",
            ),
        )
    }

    private companion object {
        const val ENDPOINT_COUNT = 3
    }
}
