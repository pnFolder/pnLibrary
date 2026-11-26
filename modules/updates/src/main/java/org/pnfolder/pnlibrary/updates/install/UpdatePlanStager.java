package org.pnfolder.pnlibrary.updates.install;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.release.ArtifactDescriptor;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease;
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan;
import org.pnfolder.pnlibrary.common.jvm.JavaRuntime;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactDownloader;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactSpecification;
import org.pnfolder.pnlibrary.updates.engine.net.TrustedHosts;
import org.pnfolder.pnlibrary.updates.engine.net.TrustedHttpClient;
import org.pnfolder.pnlibrary.updates.engine.transaction.TransactionArtifact;
import org.pnfolder.pnlibrary.updates.engine.transaction.UpdateTransaction;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;

/** Downloads every artifact in an update plan and prepares one restart transaction. */
public final class UpdatePlanStager {
    private final PlatformType platform;
    private final LibraryPaths paths;
    private final TrustedHttpClient http;
    private final UpdateTransaction transaction;
    private final long maximumArtifactBytes;
    private final boolean requireSha256;
    private final Function<ProductId, Integer> javaChoice;
    private final Function<ProductId, Integer> installedJava;

    /**
     * Creates a stager.
     *
     * @param javaChoice the Java build the administrator chose for a product, if any
     * @param installedJava the Java version the installed build of a product was made for, if known
     */
    public UpdatePlanStager(@NotNull PlatformType platform, @NotNull LibraryPaths paths, @NotNull TrustedHttpClient http,
                            @NotNull UpdateTransaction transaction, long maximumArtifactBytes, boolean requireSha256,
                            @NotNull Function<ProductId, Integer> javaChoice,
                            @NotNull Function<ProductId, Integer> installedJava) {
        this.platform = platform;
        this.paths = paths;
        this.http = http;
        this.transaction = transaction;
        this.maximumArtifactBytes = maximumArtifactBytes;
        this.requireSha256 = requireSha256;
        this.javaChoice = javaChoice;
        this.installedJava = installedJava;
    }

    /** Creates a stager that requires a SHA-256 and has no Java build preference. */
    public UpdatePlanStager(@NotNull PlatformType platform, @NotNull LibraryPaths paths, @NotNull TrustedHttpClient http,
                            @NotNull UpdateTransaction transaction, long maximumArtifactBytes) {
        this(platform, paths, http, transaction, maximumArtifactBytes, true, product -> null, product -> null);
    }

    /** Downloads the artifacts of {@code plan} and hands them to the transaction as one set for the next restart. */
    public void stage(@NotNull UUID planId, @NotNull UpdatePlan plan,
                      @NotNull Map<ProductId, InstalledUpdateTarget> installedTargets) throws IOException {
        stage(planId, plan, installedTargets, null);
    }

    /** Downloads the artifacts of {@code plan} and hands them to the transaction as one set for the next restart. */
    public void stage(@NotNull UUID planId, @NotNull UpdatePlan plan,
                      @NotNull Map<ProductId, InstalledUpdateTarget> installedTargets,
                      @Nullable UUID configurationSnapshot) throws IOException {
        Path stagingDirectory = paths.getStaging().resolve(planId.toString());
        List<TransactionArtifact> artifacts = new ArrayList<>();
        for (ProductChange change : plan.getChanges()) {
            artifacts.add(prepareArtifact(change, plan, stagingDirectory, installedTargets.get(change.getProduct())));
        }
        transaction.prepareForRestart(artifacts, planId, plan.getTargetApi(), configurationSnapshot);
    }

    private TransactionArtifact prepareArtifact(ProductChange change, UpdatePlan plan, Path stagingDirectory,
                                                @Nullable InstalledUpdateTarget installedTarget) throws IOException {
        ProductRelease release = selectedRelease(plan, change);
        ArtifactDescriptor artifact = selectArtifact(change, release,
                installedTarget == null ? null : installedTarget.getCurrentJar().getFileName().toString());
        if (requireSha256 && artifact.getSha256() == null) {
            throw new IllegalArgumentException("Release artifact has no published SHA-256: " + artifact.getFile());
        }
        URI downloadUri = artifact.getDownloadUri();
        if (downloadUri == null) {
            throw new IllegalArgumentException("Release artifact has no verified download URL: " + artifact.getFile());
        }
        TrustedHosts.requireDownload(downloadUri);
        Path stagedFile = stagingDirectory.resolve(change.getProduct().getValue()).resolve(artifact.getFile());
        download(downloadUri, stagedFile);

        ArtifactSpecification specification = new ArtifactSpecification(change.getProduct(), change.getTo(),
                release.getSupportedApi(), artifact.getFile(), artifact.getSize(), artifact.getSha256());
        Path target = installedTarget == null
                ? paths.getServerUpdateFolder().resolve(artifact.getFile())
                : installedTarget.getUpdateDirectory().resolve(installedTarget.getCurrentJar().getFileName());
        return new TransactionArtifact(specification, stagedFile, target,
                installedTarget == null ? target : installedTarget.getCurrentJar(),
                change.getFrom() == null ? null : change.getFrom().toString(),
                installedTarget == null ? null : installedTarget.getSupportedApi(),
                installedTarget == null ? paths.getPlugins().resolve(artifact.getFile()) : installedTarget.getCurrentJar());
    }

    private void download(URI source, Path stagedFile) throws IOException {
        // The body goes straight to disk, so a large file never sits in memory.
        http.stream(source, maximumArtifactBytes,
                input -> new ArtifactDownloader(maximumArtifactBytes).download(() -> input, stagedFile));
    }

    private ProductRelease selectedRelease(UpdatePlan plan, ProductChange change) {
        ProductRelease found = null;
        for (ProductRelease release : plan.getSelected()) {
            if (!release.getProduct().equals(change.getProduct())) {
                continue;
            }
            if (found != null) {
                throw new IllegalArgumentException("Collection contains more than one matching element.");
            }
            found = release;
        }
        if (found == null) {
            throw new NoSuchElementException("Collection contains no element matching the predicate.");
        }
        return found;
    }

    /**
     * Keeps the variant that is installed: a universal JAR (the one the bootstrap puts into {@code plugins}) is
     * replaced by the newer universal one, a platform JAR by the platform one. A fresh install prefers the platform
     * JAR, then the universal. When a release has builds for several Java versions, the one the administrator
     * chose wins, then the one that matches the installed product.
     */
    private ArtifactDescriptor selectArtifact(ProductChange change, ProductRelease release, @Nullable String installedFile) {
        List<ArtifactDescriptor> fitting = new ArrayList<>();
        for (ArtifactDescriptor artifact : release.getArtifacts()) {
            if (artifact.matchesPlatform(platform) && artifact.supports(JavaRuntime.getFeature())) {
                fitting.add(artifact);
            }
        }
        List<ArtifactDescriptor> candidates = preferredBuilds(change.getProduct(), fitting);
        boolean universalInstalled = installedFile != null && installedFile.toLowerCase(java.util.Locale.ROOT).endsWith("-universal.jar");
        PlatformType preferred = universalInstalled ? PlatformType.UNIVERSAL : platform;
        ArtifactDescriptor chosen = firstOn(candidates, preferred);
        if (chosen == null) {
            chosen = firstOn(candidates, platform);
        }
        if (chosen == null && !candidates.isEmpty()) {
            chosen = candidates.get(0);
        }
        if (chosen == null) {
            throw new IllegalStateException("No " + platform.getId() + " artifact for " + change.getProduct() + " " + change.getTo());
        }
        return chosen;
    }

    // The build the administrator chose, else the one that matches the installed product, else any that fits.
    private List<ArtifactDescriptor> preferredBuilds(ProductId product, List<ArtifactDescriptor> fitting) {
        for (Integer level : new Integer[] {javaChoice.apply(product), installedJava.apply(product)}) {
            if (level == null) {
                continue;
            }
            List<ArtifactDescriptor> matching = new ArrayList<>();
            for (ArtifactDescriptor artifact : fitting) {
                if (artifact.getMinimumJava() == level) {
                    matching.add(artifact);
                }
            }
            if (!matching.isEmpty()) {
                return matching;
            }
        }
        return fitting;
    }

    @Nullable
    private ArtifactDescriptor firstOn(List<ArtifactDescriptor> candidates, PlatformType type) {
        for (ArtifactDescriptor artifact : candidates) {
            if (artifact.getPlatform() == type) {
                return artifact;
            }
        }
        return null;
    }
}
