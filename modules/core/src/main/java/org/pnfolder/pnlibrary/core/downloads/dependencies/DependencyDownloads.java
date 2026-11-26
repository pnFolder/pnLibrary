package org.pnfolder.pnlibrary.core.downloads.dependencies;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.dependencies.PluginDependency;
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalArtifact;
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency;
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration;
import org.pnfolder.pnlibrary.api.downloads.DownloadSnapshot;
import org.pnfolder.pnlibrary.api.downloads.FileDownload;
import org.pnfolder.pnlibrary.api.downloads.FileDownloads;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.common.lang.Attempt;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.downloads.fetch.DownloadVerifier;
import org.pnfolder.pnlibrary.core.downloads.fetch.PluginJarVerifier;
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging;
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnerMetadata;
import org.pnfolder.pnlibrary.spi.platform.PlatformPluginInventory;
import org.pnfolder.pnlibrary.spi.platform.PlatformReadiness;

/** Finds the plugin dependencies a plugin lacks, tells the administrator, and registers their download. */
public final class DependencyDownloads {
    private static final String DEPENDENCY_PREFIX = "dependency:";

    /** Registers the files of a request; the dependency downloads go through the service that owns the executor. */
    @FunctionalInterface
    public interface Registrar {
        /** Registers {@code request} for {@code owner}; every downloaded file is also checked by {@code verifier}. */
        @NotNull DownloadRegistration register(@NotNull Object owner, @NotNull FileDownloads request,
                                               @NotNull DownloadVerifier verifier);
    }

    private final PlatformOwnerMetadata ownership;
    private final PlatformPluginInventory inventory;
    private final PlatformReadiness readiness;
    private final PlatformLogging logging;
    private final DependencyDownloadRequestFactory requests;
    private final DependencyDownloadReporter reporter;
    private final DownloadedDependencyStore store;
    private final PluginJarVerifier jarVerifier;
    private final BooleanSupplier automatic;
    private final MissingDependencies missing = new MissingDependencies();

    /** Creates the part that handles dependencies; every collaborator is built by the download service. */
    public DependencyDownloads(@NotNull PlatformOwnerMetadata ownership, @NotNull PlatformPluginInventory inventory,
                               @NotNull PlatformReadiness readiness, @NotNull PlatformLogging logging,
                               @NotNull DependencyDownloadRequestFactory requests,
                               @NotNull DependencyDownloadReporter reporter, @NotNull DownloadedDependencyStore store,
                               @NotNull PluginJarVerifier jarVerifier, @NotNull BooleanSupplier automatic) {
        this.ownership = ownership;
        this.inventory = inventory;
        this.readiness = readiness;
        this.logging = logging;
        this.requests = requests;
        this.reporter = reporter;
        this.store = store;
        this.jarVerifier = jarVerifier;
        this.automatic = automatic;
    }

    /**
     * Registers the missing external {@code dependencies} through {@code registrar}, announces what is missing and
     * returns the registration; null when nothing can be downloaded.
     */
    public @Nullable DownloadRegistration register(@NotNull Object owner,
                                                   @NotNull List<? extends PluginDependency> dependencies,
                                                   @NotNull Registrar registrar) {
        Map<String, String> installed = installedPlugins(owner);
        readiness.whenServerReady(() -> confirmDownloaded(owner, installedPlugins(owner)));
        List<ExternalPluginDependency> lacking = requests.missing(dependencies, installed);
        DependencyDownloadRequest download = requests.create(dependencies, installed);
        DownloadRegistration registration = null;
        if (download != null) {
            registration = registrar.register(owner, download.getRequest(), (declaration, path) -> {
                ExternalPluginDependency requirement = download.getRequirements().get(declaration.getKey());
                if (requirement != null) {
                    jarVerifier.verify(path, requirement);
                }
            });
        }
        if (!lacking.isEmpty()) {
            announce(owner, lacking, registration);
        }
        return registration;
    }

    /** Dependencies still missing, with the code that installs each group. */
    public @NotNull List<MissingGroup> groups() {
        return missing.pending();
    }

    /** Installs the group with this code now; null when no pending group has it. */
    public @Nullable CompletionStage<List<DownloadSnapshot>> install(@NotNull String code) {
        MissingGroup group = missing.find(code);
        return group == null ? null : group.install();
    }

    /** Remembers the dependency files among {@code declarations} until a restart confirms them. */
    public void remember(@NotNull List<FileDownload> declarations) throws IOException {
        List<String> names = new ArrayList<String>();
        for (FileDownload file : declarations) {
            if (file.getKey().startsWith(DEPENDENCY_PREFIX)) {
                names.add(file.getKey().substring(DEPENDENCY_PREFIX.length()));
            }
        }
        if (!names.isEmpty()) {
            store.record(names);
        }
    }

    /** Tells the administrator that the dependency files are staged; a failure of the report is logged. */
    public void reportStaged(@NotNull Object owner, @NotNull List<FileDownload> declarations) {
        Failures.runCatchingNonFatal(() -> {
            reporter.staged(owner, declarations);
            return null;
        }).onFailure(error -> logging.log(owner, LogLevel.ERROR, "Could not report the staged dependencies", error));
    }

    /** Tells the administrator that a dependency download failed; a failure of the report is logged. */
    public void reportFailure(@NotNull Object owner, @NotNull List<FileDownload> declarations,
                              @NotNull Throwable error) {
        boolean required = false;
        for (FileDownload declaration : declarations) {
            required |= declaration.getRequired();
        }
        LogLevel level = required ? LogLevel.ERROR : LogLevel.WARNING;
        Failures.runCatchingNonFatal(() -> {
            reporter.failure(owner, error, level);
            return null;
        }).onFailure(failure ->
            logging.log(owner, LogLevel.ERROR, "Could not report the failed dependency download", failure));
    }

    private Map<String, String> installedPlugins(Object owner) {
        Attempt<Map<String, String>> attempt = Failures.runCatchingNonFatal(inventory::installedPlugins);
        attempt.onFailure(error -> logging.log(owner, LogLevel.WARNING,
            "Could not read installed plugins; dependencies are treated as missing: " + error.getMessage()));
        Map<String, String> installed = attempt.getOrNull();
        return installed == null ? Collections.<String, String>emptyMap() : installed;
    }

    private void announce(Object owner, List<ExternalPluginDependency> lacking,
                          @Nullable DownloadRegistration registration) {
        List<MissingDependency> items = new ArrayList<MissingDependency>();
        for (ExternalPluginDependency dependency : lacking) {
            items.add(describe(dependency));
        }
        MissingGroup group = missing.add(ownerName(owner), items, registration);
        boolean installingNow = registration != null && automatic.getAsBoolean();
        Failures.runCatchingNonFatal(() -> {
            reporter.missing(owner, group, installingNow);
            return null;
        }).onFailure(error -> logging.log(owner, LogLevel.ERROR, "Could not report the missing dependencies", error));
    }

    private MissingDependency describe(ExternalPluginDependency dependency) {
        ExternalArtifact file = requests.fileFor(dependency);
        List<URI> sources = new ArrayList<URI>();
        if (file != null) {
            sources.add(file.getUri());
            sources.addAll(file.getMirrors());
        }
        boolean several = dependency.getArtifacts().size() > 1;
        return new MissingDependency(dependency.getPlugin(), dependency.getMinimumVersion().toString(),
            dependency.getDownloadPage(), sources)
            .withTitle(dependency.getTitle())
            .withDescription(dependency.getDescription())
            .withRequired(dependency.getRequired())
            .withChecked(file != null && file.getSha256() != null)
            .withVariant(file != null && several ? file.getLabel() : null)
            .withNoFileForServer(file == null && !dependency.getArtifacts().isEmpty());
    }

    private String ownerName(Object owner) {
        Map<String, String> details = ownership.ownerDetails(owner);
        String name = details == null ? null : details.get("name");
        return name != null ? name : PnLibraryIdentity.NAME;
    }

    private void confirmDownloaded(Object owner, Map<String, String> installed) {
        try {
            List<DownloadedDependency> confirmed = store.consumeInstalled(installed);
            if (!confirmed.isEmpty()) {
                reporter.connected(owner, confirmed);
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
