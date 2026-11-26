package org.pnfolder.pnlibrary.core.upload;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Tries independent upload providers in deterministic configuration order.
 *
 * <p>A provider failure does not prevent later providers from running. If every provider fails, the thrown
 * {@link IOException} contains a bounded summary identifying each failed backend. Deletion is routed directly to the
 * backend recorded in the receipt and is never attempted against unrelated providers.
 */
public final class UploadProviderChain implements UploadProvider {
    private final List<UploadProvider> providers;

    /**
     * @param providers non-empty ordered fallback list
     * @throws IllegalArgumentException if {@code providers} is empty
     */
    public UploadProviderChain(@NotNull List<UploadProvider> providers) {
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("At least one upload provider is required");
        }
        this.providers = providers;
    }

    @Override
    public @NotNull String getBackendId() {
        return "provider-chain";
    }

    @Override
    public @NotNull UploadReceipt upload(@NotNull String payload) throws IOException {
        List<String> failures = new ArrayList<String>();
        for (UploadProvider provider : providers) {
            try {
                return provider.upload(payload);
            } catch (Exception error) {
                failures.add(describe(provider, error));
            }
        }
        throw exhausted(failures);
    }

    @Override
    public @NotNull UploadReceipt uploadFile(@NotNull Path file, @NotNull String contentType) throws IOException {
        List<String> failures = new ArrayList<String>();
        for (UploadProvider provider : providers) {
            try {
                return provider.uploadFile(file, contentType);
            } catch (Exception error) {
                failures.add(describe(provider, error));
            }
        }
        throw exhausted(failures);
    }

    @Override
    public boolean delete(@NotNull UploadReceipt receipt) throws IOException {
        for (UploadProvider provider : providers) {
            if (provider.getBackendId().equals(receipt.getBackend())) {
                return provider.delete(receipt);
            }
        }
        return false;
    }

    private static String describe(UploadProvider provider, Exception error) {
        return provider.getBackendId() + ": " + (error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName());
    }

    private static IOException exhausted(List<String> failures) {
        return new IOException("Every upload provider failed: " + String.join("; ", failures));
    }
}
