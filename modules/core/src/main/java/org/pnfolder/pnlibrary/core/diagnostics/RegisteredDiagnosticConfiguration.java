package org.pnfolder.pnlibrary.core.diagnostics;

import java.nio.file.Path;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration;

/** A configuration declaration resolved against the owning plugin directory. */
public final class RegisteredDiagnosticConfiguration {
    private final String plugin;
    private final Path dataDirectory;
    private final DiagnosticConfiguration configuration;

    /** Creates a declaration of {@code configuration} for {@code plugin}, read from {@code dataDirectory}. */
    public RegisteredDiagnosticConfiguration(@NotNull String plugin, @Nullable Path dataDirectory,
                                             @NotNull DiagnosticConfiguration configuration) {
        this.plugin = plugin;
        this.dataDirectory = dataDirectory;
        this.configuration = configuration;
    }

    /** The normalized plugin key the declaration belongs to. */
    @NotNull
    public String getPlugin() {
        return plugin;
    }

    /** The plugin's data directory, or {@code null} when the library's own folder applies. */
    @Nullable
    public Path getDataDirectory() {
        return dataDirectory;
    }

    /** The declared file with its hiding rules. */
    @NotNull
    public DiagnosticConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RegisteredDiagnosticConfiguration)) {
            return false;
        }
        RegisteredDiagnosticConfiguration that = (RegisteredDiagnosticConfiguration) other;
        return plugin.equals(that.plugin) && Objects.equals(dataDirectory, that.dataDirectory)
            && configuration.equals(that.configuration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(plugin, dataDirectory, configuration);
    }

    @Override
    public String toString() {
        return "RegisteredDiagnosticConfiguration(plugin=" + plugin + ", dataDirectory=" + dataDirectory
            + ", configuration=" + configuration + ")";
    }
}
