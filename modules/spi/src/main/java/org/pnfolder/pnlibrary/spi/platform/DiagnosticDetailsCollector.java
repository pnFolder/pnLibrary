package org.pnfolder.pnlibrary.spi.platform;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Builds the common diagnostic envelope shared by every platform adapter. */
public final class DiagnosticDetailsCollector {
    private final Supplier<PlatformSnapshot> snapshotProvider;

    public DiagnosticDetailsCollector(@NotNull Supplier<PlatformSnapshot> snapshotProvider) {
        this.snapshotProvider = Objects.requireNonNull(snapshotProvider, "snapshotProvider");
    }

    /** Collects common details and lets the platform add its native sections. */
    @NotNull
    public Map<String, Object> collect(boolean includeSensitive, @NotNull Consumer<Map<String, Object>> enrich) {
        Map<String, Object> details = new LinkedHashMap<>(snapshotProvider.get().asMap());
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("sensitiveDataIncluded", includeSensitive);
        privacy.put("omittedFields", includeSensitive
                ? Collections.emptyList()
                : Collections.singletonList("backendAddresses"));
        details.put("privacy", privacy);

        enrich.accept(details);
        return details;
    }
}
