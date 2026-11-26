package org.pnfolder.pnlibrary.core.diagnostics.config;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsContributor;
import org.pnfolder.pnlibrary.config.ManagedConfigFile;
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry;

/** Puts configuration files that plugins register into those plugins' support reports. */
public final class ConfigFileDiagnostics {
    private ConfigFileDiagnostics() {
    }

    /**
     * Puts a file a plugin registers through the configuration service into that plugin's report, with the fields
     * marked {@code @ConfigSecret} hidden. The returned handle removes the file from the report again.
     */
    @NotNull
    public static AutoCloseable reportConfigFile(@NotNull DiagnosticsRegistry registry, @NotNull ManagedConfigFile file) {
        DiagnosticConfiguration.Builder builder = DiagnosticConfiguration.builder(file.getPath());
        for (String secret : file.getSecretPaths()) {
            builder.exclude(secret);
        }
        final DiagnosticConfiguration configuration = builder.build();
        final String id = "config:" + file.getPath();
        DiagnosticsContributor contributor = new DiagnosticsContributor() {
            @NotNull
            @Override
            public String getId() {
                return id;
            }

            @NotNull
            @Override
            public Map<String, Object> collect() {
                return Collections.emptyMap();
            }

            @NotNull
            @Override
            public Collection<DiagnosticConfiguration> configurations() {
                return Collections.singletonList(configuration);
            }
        };
        return registry.register(file.getPluginId().getValue(), file.getDirectory(), contributor);
    }
}
