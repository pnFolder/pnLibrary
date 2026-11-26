package org.pnfolder.pnlibrary.updates.engine;

import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.InstalledProduct;
import org.pnfolder.pnlibrary.api.updates.release.ProductDependency;
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Candidate releases per product: what each installed or missing product may become under one target API. */
final class ResolverDomains {
    private ResolverDomains() {
    }

    /** Releases that {@code current} may move to (or stay on) under {@code targetApi}; frozen products keep only themselves. */
    static List<ProductRelease> installedDomain(InstalledProduct current, List<ProductRelease> releases, int targetApi,
                                                boolean frozen, ResolverPolicy policy) {
        boolean currentSupportsApi = current.getSupportedApi().supports(targetApi);
        if (frozen) {
            return currentSupportsApi ? Collections.singletonList(asRelease(current)) : Collections.<ProductRelease>emptyList();
        }
        List<ProductRelease> candidates = new ArrayList<ProductRelease>();
        for (ProductRelease release : releases) {
            if (release.getProduct().equals(current.getProduct()) && release.getVersion().compareTo(current.getVersion()) >= 0
                && release.getSupportedApi().supports(targetApi) && externalPluginDependenciesAvailable(release, policy)) {
                candidates.add(release);
            }
        }
        if (currentSupportsApi) {
            candidates.add(asRelease(current));
        }
        return newestFirst(candidates);
    }

    /** Adds domains for dependencies that are not installed yet, repeating until no new product appears. */
    static void addMissingDependencyDomains(Map<ProductId, List<ProductRelease>> domains, List<ProductRelease> releases,
                                            int targetApi, ResolverPolicy policy) {
        boolean added;
        do {
            added = false;
            for (ProductId dependency : missingDependencies(domains)) {
                List<ProductRelease> candidates = new ArrayList<ProductRelease>();
                for (ProductRelease release : releases) {
                    if (release.getProduct().equals(dependency) && release.getSupportedApi().supports(targetApi)
                        && externalPluginDependenciesAvailable(release, policy)) {
                        candidates.add(release);
                    }
                }
                if (!candidates.isEmpty()) {
                    domains.put(dependency, newestFirst(candidates));
                    added = true;
                }
            }
        } while (added);
    }

    static List<ProductRelease> newestFirst(List<ProductRelease> releases) {
        Map<String, ProductRelease> distinct = new LinkedHashMap<String, ProductRelease>();
        for (ProductRelease release : releases) {
            String key = release.getVersion().toString();
            if (!distinct.containsKey(key)) {
                distinct.put(key, release);
            }
        }
        List<ProductRelease> sorted = new ArrayList<ProductRelease>(distinct.values());
        Collections.sort(sorted, new Comparator<ProductRelease>() {
            @Override
            public int compare(ProductRelease left, ProductRelease right) {
                return right.getVersion().compareTo(left.getVersion());
            }
        });
        return sorted;
    }

    static ProductRelease asRelease(InstalledProduct installed) {
        return new ProductRelease(installed.getProduct(), installed.getVersion(), UpdateChannel.STABLE,
            installed.getSupportedApi(), installed.getProvidesApi(), Collections.<ProductDependency>emptyList(), null,
            Collections.<org.pnfolder.pnlibrary.api.updates.release.ArtifactDescriptor>emptyList(),
            Collections.<ExternalPluginDependency>emptyList(), null, null, "");
    }

    static boolean externalPluginDependenciesAvailable(ProductRelease release, ResolverPolicy policy) {
        Set<String> installed = lowercase(policy.getInstalledExternalPlugins());
        for (ExternalPluginDependency dependency : release.getExternalPluginDependencies()) {
            if (!installed.contains(dependency.getPlugin().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    static Set<String> lowercase(Set<String> names) {
        Set<String> result = new java.util.HashSet<String>();
        for (String name : names) {
            result.add(name.toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private static Set<ProductId> missingDependencies(Map<ProductId, List<ProductRelease>> domains) {
        Set<ProductId> missing = new TreeSet<ProductId>();
        for (List<ProductRelease> releases : domains.values()) {
            for (ProductRelease release : releases) {
                for (ProductDependency dependency : release.getDependencies()) {
                    if (!domains.containsKey(dependency.getProduct())) {
                        missing.add(dependency.getProduct());
                    }
                }
            }
        }
        return missing;
    }
}
