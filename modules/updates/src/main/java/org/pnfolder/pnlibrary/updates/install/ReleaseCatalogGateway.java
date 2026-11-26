package org.pnfolder.pnlibrary.updates.install;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.request.CatalogSource;
import org.pnfolder.pnlibrary.updates.engine.catalog.RefreshMode;
import org.pnfolder.pnlibrary.updates.engine.catalog.ReleaseCatalog;
import org.pnfolder.pnlibrary.updates.engine.catalog.ReleaseCatalogClient;
import org.pnfolder.pnlibrary.updates.engine.catalog.ReleaseCatalogCodec;
import org.pnfolder.pnlibrary.updates.engine.net.TrustedHttpClient;

/**
 * Loads one release catalog through independent GitHub endpoints.
 *
 * <p>The GitHub endpoints run concurrently; jsDelivr is the last resort. The caller itself does not occupy the
 * request executor, preventing the nested-executor starvation that previously
 * made update checks wait forever when several products were registered.
 */
public final class ReleaseCatalogGateway {
    /** GitHub endpoints queried in parallel before jsDelivr is tried. */
    private static final int PRIMARY_ENDPOINT_COUNT = 2;

    private final ReleaseCatalogClient client;
    private final TrustedHttpClient http;
    private final Executor requestExecutor;

    /** Creates a gateway that runs its requests on {@code requestExecutor}. */
    public ReleaseCatalogGateway(@NotNull ReleaseCatalogClient client, @NotNull TrustedHttpClient http,
                                 @NotNull Executor requestExecutor) {
        this.client = client;
        this.http = http;
        this.requestExecutor = requestExecutor;
    }

    /**
     * Returns the first catalog GitHub raw or the Contents API delivers. jsDelivr caches for hours and may
     * serve a stale catalog, so it is asked only when both GitHub endpoints failed.
     */
    @NotNull
    public ReleaseCatalog load(@NotNull CatalogSource source, @NotNull RefreshMode refreshMode) {
        CatalogEndpoints endpoints = new CatalogEndpoints(source);
        CompletableFuture<ReleaseCatalog> result = new CompletableFuture<>();
        List<String> failures = Collections.synchronizedList(new ArrayList<String>());
        AtomicInteger remaining = new AtomicInteger(PRIMARY_ENDPOINT_COUNT);

        Runnable fallback = () -> submit("jsDelivr", result, failures, null, endpoints,
                () -> client.load(endpoints.cdn, RefreshMode.FORCE_REMOTE).join());
        Runnable onPrimaryFailed = () -> onAllFailed(remaining, fallback);
        submit("raw", result, failures, onPrimaryFailed, null, () -> client.load(endpoints.raw, refreshMode).join());
        submit("api", result, failures, onPrimaryFailed, null, () -> loadFromContentsApi(endpoints.api));
        return result.join();
    }

    private void onAllFailed(AtomicInteger remaining, Runnable fallback) {
        if (remaining.decrementAndGet() == 0) {
            fallback.run();
        }
    }

    private void submit(String label, CompletableFuture<ReleaseCatalog> result, List<String> failures,
                        @Nullable Runnable onFailure, @Nullable CatalogEndpoints endpoints,
                        Callable<ReleaseCatalog> request) {
        CompletableFuture.supplyAsync(() -> run(request), requestExecutor).whenComplete((catalog, error) -> {
            if (error == null) {
                result.complete(catalog);
                return;
            }
            failures.add(label + ": " + failureMessage(error));
            if (onFailure != null) {
                onFailure.run();
                return;
            }
            result.completeExceptionally(new IllegalStateException(
                    "Release catalog not found at " + (endpoints == null ? null : endpoints.raw)
                            + "; check the repository branch (PluginUpdateRequest.Builder.branch). "
                            + "Tried GitHub raw, Contents API and jsDelivr: " + String.join("; ", failures),
                    error.getCause() != null ? error.getCause() : error));
        });
    }

    private static ReleaseCatalog run(Callable<ReleaseCatalog> request) {
        try {
            return request.call();
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new CompletionException(error);
        }
    }

    private ReleaseCatalog loadFromContentsApi(URI source) throws IOException {
        byte[] response = http.get(source, ReleaseCatalogCodec.MAX_BYTES * 2);
        JsonElement content = JsonParser.parseString(new String(response, StandardCharsets.UTF_8))
                .getAsJsonObject().get("content");
        if (content == null) {
            throw new IllegalStateException("GitHub API response does not contain file content");
        }
        return new ReleaseCatalogCodec().decode(Base64.getMimeDecoder().decode(content.getAsString()));
    }

    private String failureMessage(Throwable error) {
        if (error.getCause() != null && error.getCause().getMessage() != null) {
            return error.getCause().getMessage();
        }
        return error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
    }

    private static final class CatalogEndpoints {
        private final URI raw;
        private final URI api;
        private final URI cdn;

        CatalogEndpoints(CatalogSource source) {
            this.raw = URI.create(source.getRawUrl());
            this.api = URI.create(source.getApiUrl());
            this.cdn = URI.create(source.getCdnUrl());
        }
    }
}
