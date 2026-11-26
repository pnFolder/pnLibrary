package org.pnfolder.pnlibrary.core.diagnostics;

import java.util.Objects;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticRegistration;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsContributor;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.diagnostics.redaction.DiagnosticValueSanitizer;

/** Contributor registrations and configuration declarations for one plugin. */
public final class DiagnosticContributors {
    private static final int MAXIMUM_ID_LENGTH = 96;
    private static final int MAXIMUM_CONFIGURATION_FILES = 64;

    private final DiagnosticValueSanitizer sanitizer;
    private final BiConsumer<String, Throwable> onFailure;
    private final Map<String, RegisteredContributor> contributors =
        new ConcurrentHashMap<String, RegisteredContributor>();

    /** Creates a registry that ignores failures of contributors. */
    public DiagnosticContributors(@NotNull DiagnosticValueSanitizer sanitizer) {
        this(sanitizer, (what, error) -> {
            });
    }

    /** Creates a registry that tells {@code onFailure} what failed when a contributor breaks. */
    public DiagnosticContributors(@NotNull DiagnosticValueSanitizer sanitizer,
                                  @NotNull BiConsumer<String, Throwable> onFailure) {
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
        this.onFailure = Objects.requireNonNull(onFailure, "onFailure");
    }

    /** Registers {@code contributor}; {@code dataDirectory} is where its configuration files are read from. */
    @NotNull
    public DiagnosticRegistration register(@NotNull DiagnosticsContributor contributor,
                                           @Nullable Path dataDirectory) {
        String id = sanitizer.text(contributor.getId(), MAXIMUM_ID_LENGTH);
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Contributor id must not be empty");
        }
        RegisteredContributor registered = new RegisteredContributor(contributor, dataDirectory);
        contributors.put(id, registered);
        return new ContributorRegistration(id, registered);
    }

    /** Asks every contributor for its data; a failing one is reported in its own entry. */
    @NotNull
    public Map<String, Object> collect() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, RegisteredContributor> entry : contributors.entrySet()) {
            result.put(entry.getKey(), collectOne(entry.getValue()));
        }
        return result;
    }

    /** How many contributors are registered. */
    public int size() {
        return contributors.size();
    }

    /** The configuration files declared by contributors, one per path, at most 64. */
    @NotNull
    public List<RegisteredDiagnosticConfiguration> configurations(@NotNull String plugin) {
        Map<String, RegisteredDiagnosticConfiguration> files =
            new LinkedHashMap<String, RegisteredDiagnosticConfiguration>();
        for (RegisteredContributor registered : contributors.values()) {
            collectConfigurations(plugin, registered, files);
        }
        return new ArrayList<RegisteredDiagnosticConfiguration>(files.values());
    }

    private Object collectOne(RegisteredContributor registered) {
        try {
            return sanitizer.map(registered.contributor.collect());
        } catch (Exception exception) {
            Map<String, Object> failure = new LinkedHashMap<String, Object>();
            failure.put("collectionError", sanitizer.exception(exception));
            return failure;
        }
    }

    private void collectConfigurations(String plugin, RegisteredContributor registered,
                                       Map<String, RegisteredDiagnosticConfiguration> destination) {
        try {
            for (DiagnosticConfiguration configuration : registered.contributor.configurations()) {
                if (destination.size() < MAXIMUM_CONFIGURATION_FILES && !destination.containsKey(configuration.getPath())) {
                    destination.put(configuration.getPath(),
                        new RegisteredDiagnosticConfiguration(plugin, registered.dataDirectory, configuration));
                }
            }
            for (String path : registered.contributor.configurationFiles()) {
                if (destination.size() < MAXIMUM_CONFIGURATION_FILES && !destination.containsKey(path)) {
                    declareFile(plugin, registered, path, destination);
                }
            }
        } catch (Exception error) {
            // One malformed contributor must not prevent collection from the others.
            onFailure.accept("A diagnostics contributor of " + plugin + " failed to list its configuration files", error);
        }
    }

    private void declareFile(String plugin, RegisteredContributor registered, String path,
                             Map<String, RegisteredDiagnosticConfiguration> destination) {
        // A path the declaration rules reject is skipped; the other files of this contributor still count.
        Failures.runCatchingNonFatal(() -> destination.put(path, new RegisteredDiagnosticConfiguration(
            plugin, registered.dataDirectory, DiagnosticConfiguration.builder(path).build())));
    }

    private final class ContributorRegistration implements DiagnosticRegistration {
        private final String id;
        private final RegisteredContributor registered;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        ContributorRegistration(String id, RegisteredContributor registered) {
            this.id = id;
            this.registered = registered;
        }

        @Override
        public boolean isClosed() {
            return closed.get();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                contributors.remove(id, registered);
            }
        }
    }

    // Identity equality: closing an old registration must not remove an equal one registered after it.
    private static final class RegisteredContributor {
        final DiagnosticsContributor contributor;
        final Path dataDirectory;

        RegisteredContributor(DiagnosticsContributor contributor, Path dataDirectory) {
            this.contributor = contributor;
            this.dataDirectory = dataDirectory;
        }
    }
}
