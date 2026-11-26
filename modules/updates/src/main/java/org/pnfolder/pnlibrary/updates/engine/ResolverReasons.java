package org.pnfolder.pnlibrary.updates.engine;

import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.release.InstalledProduct;
import org.pnfolder.pnlibrary.api.updates.release.ProductDependency;
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease;
import org.pnfolder.pnlibrary.api.updates.state.BlockedReason;
import org.pnfolder.pnlibrary.api.updates.state.WaitsForLibrary;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The structured blockers a resolution reports to the administrator. */
final class ResolverReasons {
    private ResolverReasons() {
    }

    /** Newer releases of installed products that the target API supports. */
    static List<ProductRelease> upgradableCandidates(List<ProductRelease> releases, Map<ProductId, InstalledProduct> installedById,
                                                     int targetApi) {
        List<ProductRelease> upgrades = new ArrayList<>();
        for (ProductRelease candidate : releases) {
            InstalledProduct current = installedById.get(candidate.getProduct());
            if (current != null && candidate.getVersion().compareTo(current.getVersion()) > 0
                && candidate.getSupportedApi().supports(targetApi)) {
                upgrades.add(candidate);
            }
        }
        return upgrades;
    }

    /** The newest newer release of {@code current} that needs a newer API than the library will have, if any. */
    static List<BlockedReason> waitsForLibrary(InstalledProduct current, List<ProductRelease> releases, int libraryApi) {
        ProductRelease waiting = null;
        for (ProductRelease release : releases) {
            if (release.getProduct().equals(current.getProduct()) && release.getVersion().compareTo(current.getVersion()) > 0
                && release.getSupportedApi().getMinimum() > libraryApi
                && (waiting == null || release.getVersion().compareTo(waiting.getVersion()) > 0)) {
                waiting = release;
            }
        }
        List<BlockedReason> notes = new ArrayList<>();
        if (waiting != null) {
            notes.add(new WaitsForLibrary(current.getProduct(), waiting.getVersion(),
                waiting.getSupportedApi().getMinimum(), libraryApi));
        }
        return notes;
    }

    static List<BlockedReason> missingDependency(List<ProductRelease> upgrades, Map<ProductId, List<ProductRelease>> domains) {
        Set<BlockedReason> reasons = new LinkedHashSet<>();
        for (ProductRelease candidate : upgrades) {
            for (ProductDependency dependency : candidate.getDependencies()) {
                if (!domains.containsKey(dependency.getProduct())) {
                    reasons.add(new BlockedReason.MissingDependency(candidate.getProduct(), dependency.getProduct(),
                        dependency.getMinimumVersion()));
                }
            }
        }
        return new ArrayList<>(reasons);
    }

    static List<BlockedReason> missingExternalPlugin(List<ProductRelease> upgrades, ResolverPolicy policy) {
        Set<String> installedExternal = ResolverDomains.lowercase(policy.getInstalledExternalPlugins());
        Set<BlockedReason> reasons = new LinkedHashSet<>();
        for (ProductRelease release : upgrades) {
            for (ExternalPluginDependency dependency : release.getExternalPluginDependencies()) {
                if (!installedExternal.contains(dependency.getPlugin().toLowerCase(Locale.ROOT))) {
                    reasons.add(new BlockedReason.MissingExternalPluginDependency(release.getProduct(), dependency.getPlugin(),
                        dependency.getMinimumVersion(),
                        dependency.getDownloadPage() == null ? null : dependency.getDownloadPage().toString()));
                }
            }
        }
        return new ArrayList<>(reasons);
    }

    static List<BlockedReason> dependency(Map<ProductId, List<ProductRelease>> domains, ProductId library, int targetApi) {
        Set<BlockedReason> reasons = new LinkedHashSet<>();
        for (List<ProductRelease> releases : domains.values()) {
            for (ProductRelease release : releases) {
                for (ProductDependency dependency : release.getDependencies()) {
                    if (!isAvailable(domains.get(dependency.getProduct()), dependency.getMinimumVersion())) {
                        reasons.add(new BlockedReason.MissingDependency(release.getProduct(), dependency.getProduct(),
                            dependency.getMinimumVersion()));
                    }
                }
            }
        }
        if (reasons.isEmpty()) {
            reasons.add(new BlockedReason.NoCompatibleRelease(library, targetApi));
        }
        return new ArrayList<>(reasons);
    }

    private static boolean isAvailable(List<ProductRelease> candidates, SemanticVersion minimum) {
        if (candidates == null) {
            return false;
        }
        SemanticVersion newest = null;
        for (ProductRelease candidate : candidates) {
            if (newest == null || candidate.getVersion().compareTo(newest) > 0) {
                newest = candidate.getVersion();
            }
        }
        return newest != null && newest.compareTo(minimum) >= 0;
    }
}
