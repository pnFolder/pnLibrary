package org.pnfolder.pnlibrary.api.text.cache;

import java.util.Objects;

/** Point-in-time component-cache counters. */
public final class ComponentCacheStatistics {
    private final long hits;
    private final long misses;
    private final long evictions;
    private final int size;

    /**
     * @param hits successful cache lookups
     * @param misses lookups that required parsing
     * @param evictions entries removed by capacity or expiry policy
     * @param size entries retained when the snapshot was created
     */
    public ComponentCacheStatistics(long hits, long misses, long evictions, int size) {
        this.hits = hits;
        this.misses = misses;
        this.evictions = evictions;
        this.size = size;
    }

    /** The successful cache lookups. */
    public long getHits() {
        return hits;
    }

    /** The lookups that required parsing. */
    public long getMisses() {
        return misses;
    }

    /** The entries removed by capacity or expiry policy. */
    public long getEvictions() {
        return evictions;
    }

    /** The entries retained when the snapshot was created. */
    public int getSize() {
        return size;
    }

    /** Number of lookups: hits plus misses. */
    public long getLookups() {
        return hits + misses;
    }

    /** Ratio of hits to all lookups, or {@code 0.0} before the first lookup. */
    public double getHitRate() {
        long lookups = getLookups();
        return lookups == 0L ? 0.0 : (double) hits / lookups;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ComponentCacheStatistics)) {
            return false;
        }
        ComponentCacheStatistics that = (ComponentCacheStatistics) other;
        return hits == that.hits && misses == that.misses && evictions == that.evictions && size == that.size;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hits, misses, evictions, size);
    }

    @Override
    public String toString() {
        return "ComponentCacheStatistics(hits=" + hits + ", misses=" + misses + ", evictions=" + evictions
            + ", size=" + size + ")";
    }
}
