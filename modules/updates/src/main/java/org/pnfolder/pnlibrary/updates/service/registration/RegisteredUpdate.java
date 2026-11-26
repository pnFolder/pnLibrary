package org.pnfolder.pnlibrary.updates.service.registration;

import java.util.Objects;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.ArtifactDescriptor;
import org.pnfolder.pnlibrary.api.updates.release.InstalledProduct;
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease;
import org.pnfolder.pnlibrary.api.updates.release.ReleaseSummary;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateArtifact;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.common.jvm.JavaRuntime;
import org.pnfolder.pnlibrary.updates.engine.catalog.CatalogArtifact;
import org.pnfolder.pnlibrary.updates.engine.catalog.CatalogRelease;
import org.pnfolder.pnlibrary.updates.policy.ReleaseChannelSelection;
import org.pnfolder.pnlibrary.updates.policy.ReleaseChannelSelector;

/** Runtime state and public handle for one component participating in updates. */
public final class RegisteredUpdate implements UpdateRegistration {
    private final ProductDescriptor descriptor;
    private final PluginUpdateRequest request;
    private final PluginUpdateArtifact artifact;
    private final Path jar;
    private final Path updateDirectory;
    private final Supplier<UpdateChannel> selectedChannel;
    private final Supplier<CompletionStage<UpdatePlanSnapshot>> checkForUpdates;
    private final Function<UpdatePlanSnapshot, CompletionStage<UpdatePlanSnapshot>> stagePlan;
    private final Consumer<RegisteredUpdate> onClose;
    private final BooleanSupplier effectiveAutomatic;
    private final PlatformType platform;
    private final Supplier<Integer> chosenJava;
    private final String product;
    private final String version;
    private final String repositoryPath;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicReference<UpdateSnapshot> state;

    /**
     * Creates the handle of one registered component.
     *
     * @param effectiveAutomatic whether automatic download is on for it right now
     * @param platform the platform the artifacts must fit, or null for any
     * @param chosenJava the Java build the administrator chose, or null
     */
    public RegisteredUpdate(@NotNull ProductDescriptor descriptor, @NotNull PluginUpdateRequest request,
                            @NotNull PluginUpdateArtifact artifact, @NotNull Path jar, @NotNull Path updateDirectory,
                            @NotNull Supplier<UpdateChannel> selectedChannel,
                            @NotNull Supplier<CompletionStage<UpdatePlanSnapshot>> checkForUpdates,
                            @NotNull Function<UpdatePlanSnapshot, CompletionStage<UpdatePlanSnapshot>> stagePlan,
                            @NotNull Consumer<RegisteredUpdate> onClose, @NotNull BooleanSupplier effectiveAutomatic,
                            @Nullable PlatformType platform, @NotNull Supplier<Integer> chosenJava) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.request = Objects.requireNonNull(request, "request");
        this.artifact = Objects.requireNonNull(artifact, "artifact");
        this.jar = Objects.requireNonNull(jar, "jar");
        this.updateDirectory = Objects.requireNonNull(updateDirectory, "updateDirectory");
        this.selectedChannel = Objects.requireNonNull(selectedChannel, "selectedChannel");
        this.checkForUpdates = Objects.requireNonNull(checkForUpdates, "checkForUpdates");
        this.stagePlan = Objects.requireNonNull(stagePlan, "stagePlan");
        this.onClose = Objects.requireNonNull(onClose, "onClose");
        this.effectiveAutomatic = Objects.requireNonNull(effectiveAutomatic, "effectiveAutomatic");
        this.platform = platform;
        this.chosenJava = Objects.requireNonNull(chosenJava, "chosenJava");
        this.product = descriptor.getId().getValue();
        this.version = descriptor.getVersion().toString();
        this.repositoryPath = request.getRepositoryOwner() + "/" + request.getRepositoryName();
        this.state = new AtomicReference<>(initialSnapshot());
    }

    /** Creates a handle that follows the request's own automatic-download choice and has no platform or Java preference. */
    public RegisteredUpdate(@NotNull ProductDescriptor descriptor, @NotNull PluginUpdateRequest request,
                            @NotNull PluginUpdateArtifact artifact, @NotNull Path jar, @NotNull Path updateDirectory,
                            @NotNull Supplier<UpdateChannel> selectedChannel,
                            @NotNull Supplier<CompletionStage<UpdatePlanSnapshot>> checkForUpdates,
                            @NotNull Function<UpdatePlanSnapshot, CompletionStage<UpdatePlanSnapshot>> stagePlan,
                            @NotNull Consumer<RegisteredUpdate> onClose) {
        this(descriptor, request, artifact, jar, updateDirectory, selectedChannel, checkForUpdates, stagePlan, onClose,
                request::getAutomaticDownload, null, () -> null);
    }

    /** Creates a handle with an explicit automatic-download choice and no platform or Java preference. */
    public RegisteredUpdate(@NotNull ProductDescriptor descriptor, @NotNull PluginUpdateRequest request,
                            @NotNull PluginUpdateArtifact artifact, @NotNull Path jar, @NotNull Path updateDirectory,
                            @NotNull Supplier<UpdateChannel> selectedChannel,
                            @NotNull Supplier<CompletionStage<UpdatePlanSnapshot>> checkForUpdates,
                            @NotNull Function<UpdatePlanSnapshot, CompletionStage<UpdatePlanSnapshot>> stagePlan,
                            @NotNull Consumer<RegisteredUpdate> onClose, @NotNull BooleanSupplier effectiveAutomatic) {
        this(descriptor, request, artifact, jar, updateDirectory, selectedChannel, checkForUpdates, stagePlan, onClose,
                effectiveAutomatic, null, () -> null);
    }

    /** The product as the plugin described it. */
    @NotNull
    public ProductDescriptor getDescriptor() {
        return descriptor;
    }

    /** What the plugin asked for when it registered. */
    @NotNull
    public PluginUpdateRequest getRequest() {
        return request;
    }

    /** The jar the plugin runs from. */
    @NotNull
    public Path getJar() {
        return jar;
    }

    /** The folder the server picks prepared jars up from. */
    @NotNull
    public Path getUpdateDirectory() {
        return updateDirectory;
    }

    /** The normalized product ID. */
    @NotNull
    public String getProduct() {
        return product;
    }

    /** The installed version. */
    @NotNull
    public String getVersion() {
        return version;
    }

    @NotNull
    @Override
    public String getRepository() {
        return repositoryPath;
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @NotNull
    @Override
    public UpdateSnapshot getSnapshot() {
        return state.get();
    }

    @Override
    public void checkNow() {
        checkOpen();
        checkForUpdates.get();
    }

    @Override
    public void downloadNow() {
        checkOpen();
        checkForUpdates.get().thenCompose(stagePlan);
    }

    /** The product as the resolver sees it installed. */
    @NotNull
    public InstalledProduct installedProduct(@Nullable Integer providedApi) {
        return new InstalledProduct(descriptor.getId(), descriptor.getVersion(), descriptor.getSupportedApi(), providedApi);
    }

    /** Converts a catalog release into a resolver release for this product. */
    @NotNull
    public ProductRelease releaseFrom(@NotNull CatalogRelease catalogRelease) {
        List<ArtifactDescriptor> artifacts = new ArrayList<>();
        for (CatalogArtifact item : catalogRelease.getArtifacts()) {
            artifacts.add(new ArtifactDescriptor(item.getFile(), item.getPlatform(), item.getJavaMinimum(),
                    item.getJavaMaximum(), item.getSize(), item.getSha256(), item.getUrl()));
        }
        boolean library = descriptor.getId().getValue().equals(PnLibraryIdentity.PRODUCT_ID);
        return new ProductRelease(descriptor.getId(), catalogRelease.getVersion(), catalogRelease.getChannel(),
                catalogRelease.getApi(), library ? Integer.valueOf(catalogRelease.getApi().getMaximum()) : null,
                Collections.emptyList(), repositoryPath, artifacts, Collections.emptyList(),
                catalogRelease.getPublishedAt(),
                catalogRelease.getChangelogUrl() == null ? null : catalogRelease.getChangelogUrl().toString(),
                catalogRelease.getDescription());
    }

    /** Remembers the releases found for {@code channel} and updates the snapshot. */
    public void observe(@NotNull List<ProductRelease> releases, @NotNull UpdateChannel channel) {
        if (closed.get()) {
            return;
        }
        ReleaseChannelSelection selection = ReleaseChannelSelector.select(descriptor.getId(), releases, channel);
        ProductRelease latest = selection.getLatestAllowed();
        List<Integer> builds = javaBuilds(latest);
        Integer recommended = builds.contains(descriptor.getMinimumJava()) ? Integer.valueOf(descriptor.getMinimumJava())
                : builds.isEmpty() ? null : builds.get(builds.size() - 1);
        List<ProductRelease> sorted = new ArrayList<>(selection.getAll());
        Collections.sort(sorted, (left, right) -> right.getVersion().compareTo(left.getVersion()));
        List<ReleaseSummary> summaries = new ArrayList<>();
        for (ProductRelease release : sorted) {
            summaries.add(new ReleaseSummary(release.getVersion().toString(), release.getChannel(),
                    release.getPublishedAt(), release.getChangelogUrl(), release.getSupportedApi()));
        }
        boolean newer = latest != null && latest.getVersion().compareTo(descriptor.getVersion()) > 0;
        state.set(new UpdateSnapshot(product, version, latest == null ? null : latest.getVersion().toString(), channel,
                newer ? UpdateState.UPDATE_AVAILABLE : UpdateState.UP_TO_DATE, JavaRuntime.getFeature(),
                artifact.getMinimumJava(), effectiveAutomatic.getAsBoolean(), "https://github.com/" + repositoryPath + "/releases",
                null, latest == null ? null : latest.getChangelogUrl(), request.getSupportedApi(), summaries, builds,
                chosenJava.get(), recommended));
    }

    private List<Integer> javaBuilds(@Nullable ProductRelease latest) {
        List<Integer> builds = new ArrayList<>();
        if (latest == null) {
            return builds;
        }
        for (ArtifactDescriptor candidate : latest.getArtifacts()) {
            boolean onPlatform = platform == null || candidate.matchesPlatform(platform);
            if (onPlatform && candidate.supports(JavaRuntime.getFeature())
                    && !builds.contains(candidate.getMinimumJava())) {
                builds.add(candidate.getMinimumJava());
            }
        }
        Collections.sort(builds);
        return builds;
    }

    /** Re-reads the channel and automatic-download choice after an administrator changed them. */
    public void refreshPolicy() {
        state.updateAndGet(old -> new UpdateSnapshot(old.getProduct(), old.getCurrentVersion(), old.getLatestVersion(),
                selectedChannel.get(), old.getState(), old.getCurrentJava(), old.getRequiredJava(),
                effectiveAutomatic.getAsBoolean(), old.getReleaseUrl(), old.getMessage(), old.getLatestChangelogUrl(),
                old.getSupportedApi(), old.getAvailableReleases(), old.getJavaBuilds(), chosenJava.get(),
                old.getRecommendedJava()));
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            onClose.accept(this);
        }
    }

    /** Marks the registration as closed so later results are ignored. */
    public void markClosed() {
        closed.set(true);
    }

    private UpdateSnapshot initialSnapshot() {
        return new UpdateSnapshot(product, version, null, selectedChannel.get(), UpdateState.CHECKING,
                JavaRuntime.getFeature(), artifact.getMinimumJava(), effectiveAutomatic.getAsBoolean(), null, null, null,
                request.getSupportedApi(), Collections.emptyList(), Collections.emptyList(),
                null, null);
    }

    private void checkOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Update registration is closed");
        }
    }
}
