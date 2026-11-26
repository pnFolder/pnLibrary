package org.pnfolder.pnlibrary.api.text.cache;

import java.time.Duration;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Immutable limits for the parsed-component cache. */
public final class ComponentCachePolicy {
    /** A policy that never caches: every request is parsed again. */
    public static final ComponentCachePolicy DISABLED = new ComponentCachePolicy(ComponentCacheScope.NONE);

    private final ComponentCacheScope scope;
    private final int maximumEntries;
    private final Duration expireAfterAccess;

    /**
     * @param scope ownership boundary for cached entries; {@link ComponentCacheScope#NONE} disables caching
     * @param maximumEntries maximum number of retained entries
     * @param expireAfterAccess idle time after which an entry may be evicted
     * @throws IllegalArgumentException when either limit is not positive
     */
    public ComponentCachePolicy(@NotNull ComponentCacheScope scope, int maximumEntries,
                                @NotNull Duration expireAfterAccess) {
        if (maximumEntries <= 0) {
            throw new IllegalArgumentException("Cache maximumEntries must be positive");
        }
        if (expireAfterAccess.isZero() || expireAfterAccess.isNegative()) {
            throw new IllegalArgumentException("Cache expireAfterAccess must be positive");
        }
        this.scope = Objects.requireNonNull(scope, "scope");
        this.maximumEntries = maximumEntries;
        this.expireAfterAccess = expireAfterAccess;
    }

    /** Caches per plugin, at most 1000 entries, evicted after 30 idle minutes. */
    public ComponentCachePolicy() {
        this(ComponentCacheScope.PLUGIN);
    }

    /** The given scope with at most 1000 entries, evicted after 30 idle minutes. */
    public ComponentCachePolicy(@NotNull ComponentCacheScope scope) {
        this(scope, 1_000);
    }

    /** The given scope and size, evicted after 30 idle minutes. */
    public ComponentCachePolicy(@NotNull ComponentCacheScope scope, int maximumEntries) {
        this(scope, maximumEntries, Duration.ofMinutes(30));
    }

    /** The ownership boundary for cached entries; {@link ComponentCacheScope#NONE} disables caching. */
    public @NotNull ComponentCacheScope getScope() {
        return scope;
    }

    /** The maximum number of retained entries. */
    public int getMaximumEntries() {
        return maximumEntries;
    }

    /** The idle time after which an entry may be evicted. */
    public @NotNull Duration getExpireAfterAccess() {
        return expireAfterAccess;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ComponentCachePolicy)) {
            return false;
        }
        ComponentCachePolicy that = (ComponentCachePolicy) other;
        return maximumEntries == that.maximumEntries && scope == that.scope
            && expireAfterAccess.equals(that.expireAfterAccess);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scope, maximumEntries, expireAfterAccess);
    }

    @Override
    public String toString() {
        return "ComponentCachePolicy(scope=" + scope + ", maximumEntries=" + maximumEntries
            + ", expireAfterAccess=" + expireAfterAccess + ")";
    }
}
