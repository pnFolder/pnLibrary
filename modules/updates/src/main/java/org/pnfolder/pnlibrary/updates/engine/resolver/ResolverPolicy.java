package org.pnfolder.pnlibrary.updates.engine.resolver;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Environment capabilities applied during graph resolution. */
public final class ResolverPolicy {
    private final boolean allowManagedInstalls;
    private final Set<String> installedExternalPlugins;

    public ResolverPolicy() {
        this(false, Collections.emptySet());
    }

    /**
     * @param allowManagedInstalls whether missing managed products may be added
     * @param installedExternalPlugins normalized names of installed third-party plugins
     */
    public ResolverPolicy(boolean allowManagedInstalls, @NotNull Set<String> installedExternalPlugins) {
        this.allowManagedInstalls = allowManagedInstalls;
        this.installedExternalPlugins = Collections.unmodifiableSet(new LinkedHashSet<>(installedExternalPlugins));
    }

    public boolean getAllowManagedInstalls() {
        return allowManagedInstalls;
    }

    @NotNull
    public Set<String> getInstalledExternalPlugins() {
        return installedExternalPlugins;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ResolverPolicy)) {
            return false;
        }
        ResolverPolicy that = (ResolverPolicy) other;
        return allowManagedInstalls == that.allowManagedInstalls && installedExternalPlugins.equals(that.installedExternalPlugins);
    }

    @Override
    public int hashCode() {
        return 31 * (allowManagedInstalls ? 1 : 0) + installedExternalPlugins.hashCode();
    }

    @Override
    public String toString() {
        return "ResolverPolicy(allowManagedInstalls=" + allowManagedInstalls
            + ", installedExternalPlugins=" + installedExternalPlugins + ")";
    }
}
