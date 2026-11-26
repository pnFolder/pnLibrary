package org.pnfolder.pnlibrary.api.diagnostics;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Supplies a bounded snapshot owned by another plugin. Called only while a diagnostic report is being collected.
 *
 * <p>Implementations should be <b>fast and non-blocking</b>. Heavy computation is forbidden: the collection runs on a
 * background thread, but a contributor that blocks too long will be cancelled by the collector's timeout budget.
 */
@FunctionalInterface
public interface DiagnosticsContributor {
    /** Stable identifier for this contributor, max 96 chars, pattern {@code [A-Za-z0-9_.-]+}. */
    @NotNull
    default String getId() {
        return getClass().getSimpleName();
    }

    /**
     * Returns the current diagnostic snapshot. Must not throw; wrap errors in a safe result string if needed.
     */
    @NotNull
    Map<String, Object> collect();

    /** Optional list of configuration file paths relative to the plugin data folder. */
    @NotNull
    default Collection<String> configurationFiles() {
        return Collections.emptyList();
    }

    /**
     * Rich per-file rules with redaction policies. Existing implementations may keep returning only
     * {@link #configurationFiles()}.
     */
    @NotNull
    default Collection<DiagnosticConfiguration> configurations() {
        return Collections.emptyList();
    }
}
