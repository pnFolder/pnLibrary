package org.pnfolder.pnlibrary.updates.engine;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.ArtifactDescriptor;
import org.pnfolder.pnlibrary.api.updates.release.InstalledProduct;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease;
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan;
import org.pnfolder.pnlibrary.api.updates.state.BlockedReason;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic API, channel, pause, and dependency resolver for the managed components. */
public final class UpdateResolver {
    private final ProductId libraryComponent;

    public UpdateResolver(@NotNull ProductId libraryComponent) {
        this.libraryComponent = Objects.requireNonNull(libraryComponent, "libraryComponent");
    }

    /** Resolves with the stable channel for everyone, no pauses, any platform and Java, and the default policy. */
    @NotNull
    public ResolutionResult resolve(@NotNull List<InstalledProduct> installed, @NotNull List<ProductRelease> releases) {
        return resolve(installed, releases, Collections.emptyMap(), UpdateChannel.STABLE,
            Collections.emptySet(), null, Integer.MAX_VALUE, new ResolverPolicy());
    }

    /**
     * Resolves all installed products and candidate releases as one atomic ecosystem.
     *
     * @param channels channel chosen per product; {@code defaultChannel} applies to the rest
     * @param frozen products that must stay on their installed version
     * @param platform platform the artifacts must run on, or null for any
     * @param javaFeature Java feature version the artifacts must support
     */
    @NotNull
    public ResolutionResult resolve(@NotNull List<InstalledProduct> installed, @NotNull List<ProductRelease> releases,
                                    @NotNull Map<ProductId, UpdateChannel> channels, @NotNull UpdateChannel defaultChannel,
                                    @NotNull Set<ProductId> frozen, @Nullable PlatformType platform, int javaFeature,
                                    @NotNull ResolverPolicy policy) {
        InstalledProduct currentLibrary = requireLibrary(installed);
        List<ProductRelease> allowed = allowedReleases(releases, channels, defaultChannel, platform, javaFeature);
        List<ProductRelease> libraryDomain = libraryDomain(currentLibrary, allowed, frozen);
        return resolveLibraryDomain(installed, allowed, libraryDomain, currentLibrary, frozen, policy);
    }

    private InstalledProduct requireLibrary(List<InstalledProduct> installed) {
        if (installed.isEmpty()) {
            throw new IllegalArgumentException("At least one installed component is required");
        }
        Map<ProductId, InstalledProduct> installedById = new LinkedHashMap<ProductId, InstalledProduct>();
        for (InstalledProduct product : installed) {
            installedById.put(product.getProduct(), product);
        }
        if (installedById.size() != installed.size()) {
            throw new IllegalArgumentException("Installed component IDs must be unique");
        }
        InstalledProduct currentLibrary = installedById.get(libraryComponent);
        if (currentLibrary == null) {
            throw new IllegalArgumentException("Installed pnLibrary component is required");
        }
        if (currentLibrary.getProvidesApi() == null) {
            throw new IllegalArgumentException("Installed pnLibrary must provide an API generation");
        }
        return currentLibrary;
    }

    private List<ProductRelease> allowedReleases(List<ProductRelease> releases, Map<ProductId, UpdateChannel> channels,
                                                 UpdateChannel defaultChannel, PlatformType platform, int javaFeature) {
        List<ProductRelease> allowed = new ArrayList<ProductRelease>();
        for (ProductRelease release : releases) {
            UpdateChannel channel = channels.containsKey(release.getProduct()) ? channels.get(release.getProduct()) : defaultChannel;
            if (channel.accepts(release.getChannel()) && hasUsableArtifact(release, platform, javaFeature)) {
                allowed.add(release);
            }
        }
        return allowed;
    }

    private boolean hasUsableArtifact(ProductRelease release, PlatformType platform, int javaFeature) {
        if (release.getArtifacts().isEmpty()) {
            return true;
        }
        for (ArtifactDescriptor artifact : release.getArtifacts()) {
            if ((platform == null || artifact.matchesPlatform(platform)) && artifact.supports(javaFeature)) {
                return true;
            }
        }
        return false;
    }

    private List<ProductRelease> libraryDomain(InstalledProduct currentLibrary, List<ProductRelease> allowed, Set<ProductId> frozen) {
        ProductRelease current = ResolverDomains.asRelease(currentLibrary);
        if (frozen.contains(libraryComponent)) {
            return Collections.singletonList(current);
        }
        List<ProductRelease> domain = new ArrayList<ProductRelease>();
        for (ProductRelease release : allowed) {
            if (release.getProduct().equals(libraryComponent)) {
                domain.add(release);
            }
        }
        domain.add(current);
        List<ProductRelease> providing = new ArrayList<ProductRelease>();
        for (ProductRelease release : domain) {
            if (release.getProvidesApi() != null && release.getVersion().compareTo(currentLibrary.getVersion()) >= 0) {
                providing.add(release);
            }
        }
        return ResolverDomains.newestFirst(providing);
    }

    private ResolutionResult resolveLibraryDomain(List<InstalledProduct> installed, List<ProductRelease> allowed,
                                                  List<ProductRelease> libraryDomain, InstalledProduct currentLibrary,
                                                  Set<ProductId> frozen, ResolverPolicy policy) {
        List<BlockedReason> primaryFailure = null;
        for (int index = 0; index < libraryDomain.size(); index++) {
            Attempt attempt = resolveForApi(installed, allowed, libraryDomain.get(index), frozen, policy);
            if (attempt.plan != null) {
                if (!attempt.reasons.isEmpty()) {
                    return new ResolutionResult.Blocked(attempt.reasons, attempt.plan);
                }
                if (index == 0 && primaryFailure == null) {
                    return new ResolutionResult.Ready(attempt.plan);
                }
                return new ResolutionResult.Blocked(primaryFailure == null ? Collections.emptyList() : primaryFailure,
                    attempt.plan);
            }
            if (primaryFailure == null) {
                primaryFailure = attempt.reasons;
            }
        }
        List<BlockedReason> failure = primaryFailure == null ? new ArrayList<BlockedReason>() : new ArrayList<BlockedReason>(primaryFailure);
        if (frozen.contains(libraryComponent) && newerLibraryExists(allowed, currentLibrary)) {
            failure.add(0, new BlockedReason.Frozen(libraryComponent));
        }
        return new ResolutionResult.Blocked(new ArrayList<BlockedReason>(new LinkedHashSet<BlockedReason>(failure)), null);
    }

    private boolean newerLibraryExists(List<ProductRelease> allowed, InstalledProduct currentLibrary) {
        for (ProductRelease release : allowed) {
            if (release.getProduct().equals(libraryComponent) && release.getVersion().compareTo(currentLibrary.getVersion()) > 0) {
                return true;
            }
        }
        return false;
    }

    private Attempt resolveForApi(List<InstalledProduct> installed, List<ProductRelease> releases, ProductRelease libraryRelease,
                                  Set<ProductId> frozen, ResolverPolicy policy) {
        int targetApi = libraryRelease.getProvidesApi();
        Map<ProductId, List<ProductRelease>> domains = new LinkedHashMap<ProductId, List<ProductRelease>>();
        domains.put(libraryComponent, Collections.singletonList(libraryRelease));
        List<BlockedReason> reasons = installedDomains(installed, releases, targetApi, frozen, policy, domains);
        if (!reasons.isEmpty()) {
            return new Attempt(null, reasons);
        }
        if (policy.getAllowManagedInstalls()) {
            ResolverDomains.addMissingDependencyDomains(domains, releases, targetApi, policy);
        }
        List<ProductId> ids = new ArrayList<ProductId>();
        for (ProductId id : domains.keySet()) {
            if (!id.equals(libraryComponent)) {
                ids.add(id);
            }
        }
        Collections.sort(ids);
        LinkedHashMap<ProductId, ProductRelease> selected = new LinkedHashMap<ProductId, ProductRelease>();
        selected.put(libraryComponent, libraryRelease);
        LinkedHashMap<ProductId, ProductRelease> solution = ResolverSearch.search(ids, 0, domains, selected);
        if (solution == null) {
            return new Attempt(null, ResolverReasons.dependency(domains, libraryComponent, targetApi));
        }
        return plan(installed, releases, domains, solution, targetApi, policy);
    }

    private List<BlockedReason> installedDomains(List<InstalledProduct> installed, List<ProductRelease> releases, int targetApi,
                                                 Set<ProductId> frozen, ResolverPolicy policy,
                                                 Map<ProductId, List<ProductRelease>> domains) {
        List<InstalledProduct> sorted = new ArrayList<InstalledProduct>(installed);
        Collections.sort(sorted, (left, right) -> left.getProduct().compareTo(right.getProduct()));
        List<BlockedReason> reasons = new ArrayList<BlockedReason>();
        for (InstalledProduct current : sorted) {
            if (current.getProduct().equals(libraryComponent)) {
                continue;
            }
            boolean isFrozen = frozen.contains(current.getProduct());
            List<ProductRelease> domain = ResolverDomains.installedDomain(current, releases, targetApi, isFrozen, policy);
            if (domain.isEmpty()) {
                if (isFrozen) {
                    reasons.add(new BlockedReason.Frozen(current.getProduct()));
                }
                reasons.add(new BlockedReason.NoCompatibleRelease(current.getProduct(), targetApi));
            } else {
                domains.put(current.getProduct(), domain);
            }
        }
        return reasons;
    }

    private Attempt plan(List<InstalledProduct> installed, List<ProductRelease> releases,
                         Map<ProductId, List<ProductRelease>> domains, LinkedHashMap<ProductId, ProductRelease> solution,
                         int targetApi, ResolverPolicy policy) {
        Map<ProductId, InstalledProduct> installedById = new HashMap<ProductId, InstalledProduct>();
        for (InstalledProduct product : installed) {
            installedById.put(product.getProduct(), product);
        }
        List<ProductRelease> ordered = new ArrayList<ProductRelease>(ResolverSearch.retainRequired(solution, installedById.keySet()).values());
        Collections.sort(ordered, (left, right) -> left.getProduct().compareTo(right.getProduct()));
        List<ProductChange> changes = new ArrayList<ProductChange>();
        for (ProductRelease target : ordered) {
            InstalledProduct current = installedById.get(target.getProduct());
            if (current == null || !current.getVersion().equals(target.getVersion())) {
                changes.add(new ProductChange(target.getProduct(), current == null ? null : current.getVersion(), target.getVersion()));
            }
        }
        List<ProductRelease> upgrades = ResolverReasons.upgradableCandidates(releases, installedById, targetApi);
        Set<BlockedReason> reasons = new LinkedHashSet<BlockedReason>();
        if (!policy.getAllowManagedInstalls()) {
            reasons.addAll(ResolverReasons.missingDependency(upgrades, domains));
        }
        reasons.addAll(ResolverReasons.missingExternalPlugin(upgrades, policy));
        return new Attempt(new UpdatePlan(targetApi, changes, ordered), new ArrayList<BlockedReason>(reasons));
    }

    private static final class Attempt {
        private final UpdatePlan plan;
        private final List<BlockedReason> reasons;

        Attempt(UpdatePlan plan, List<BlockedReason> reasons) {
            this.plan = plan;
            this.reasons = reasons;
        }
    }
}
