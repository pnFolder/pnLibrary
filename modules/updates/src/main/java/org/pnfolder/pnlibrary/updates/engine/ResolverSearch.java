package org.pnfolder.pnlibrary.updates.engine;

import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.release.ProductDependency;
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Depth-first search for one release per product that satisfies every dependency. */
final class ResolverSearch {
    private ResolverSearch() {
    }

    /** The first complete selection in domain order, or null when none satisfies the dependencies. */
    static LinkedHashMap<ProductId, ProductRelease> search(List<ProductId> ids, int index,
                                                           Map<ProductId, List<ProductRelease>> domains,
                                                           LinkedHashMap<ProductId, ProductRelease> selected) {
        if (index == ids.size()) {
            return dependenciesSatisfied(selected) ? new LinkedHashMap<ProductId, ProductRelease>(selected) : null;
        }
        ProductId id = ids.get(index);
        for (ProductRelease candidate : domains.get(id)) {
            selected.put(id, candidate);
            LinkedHashMap<ProductId, ProductRelease> found = search(ids, index + 1, domains, selected);
            if (found != null) {
                return found;
            }
        }
        selected.remove(id);
        return null;
    }

    /** Keeps what is installed plus everything those releases depend on, transitively. */
    static LinkedHashMap<ProductId, ProductRelease> retainRequired(LinkedHashMap<ProductId, ProductRelease> selected,
                                                                   Set<ProductId> installed) {
        Set<ProductId> required = new LinkedHashSet<ProductId>(installed);
        boolean changed;
        do {
            changed = false;
            for (ProductId id : new ArrayList<ProductId>(required)) {
                ProductRelease release = selected.get(id);
                if (release == null) {
                    continue;
                }
                for (ProductDependency dependency : release.getDependencies()) {
                    if (required.add(dependency.getProduct())) {
                        changed = true;
                    }
                }
            }
        } while (changed);
        LinkedHashMap<ProductId, ProductRelease> retained = new LinkedHashMap<ProductId, ProductRelease>();
        for (Map.Entry<ProductId, ProductRelease> entry : selected.entrySet()) {
            if (required.contains(entry.getKey())) {
                retained.put(entry.getKey(), entry.getValue());
            }
        }
        return retained;
    }

    private static boolean dependenciesSatisfied(Map<ProductId, ProductRelease> selected) {
        for (ProductRelease release : selected.values()) {
            for (ProductDependency dependency : release.getDependencies()) {
                ProductRelease target = selected.get(dependency.getProduct());
                if (target == null || target.getVersion().compareTo(dependency.getMinimumVersion()) < 0) {
                    return false;
                }
            }
        }
        return true;
    }
}
