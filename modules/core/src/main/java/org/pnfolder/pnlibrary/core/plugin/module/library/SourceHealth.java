package org.pnfolder.pnlibrary.core.plugin.module.library;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.pnfolder.pnlibrary.api.libraries.LibrarySource;

/**
 * Remembers, in memory only, which places failed in the last few minutes, so the next library does not wait on a dead mirror
 * first: such a place is tried last. The order the author wrote is kept otherwise, and nothing is saved to disk.
 */
public final class SourceHealth {
    private static final long MEMORY_MILLIS = 5 * 60_000L;
    private static final Map<String, Long> FAILED_AT = new ConcurrentHashMap<>();

    private SourceHealth() {
    }

    public static void failed(String url) {
        failed(url, System.currentTimeMillis());
    }

    public static void failed(String url, long now) {
        FAILED_AT.put(url, now);
    }

    public static List<LibrarySource> order(List<LibrarySource> sources) {
        return order(sources, System.currentTimeMillis());
    }

    /** {@code sources} in their own order, except that places that failed within the last five minutes go to the end. */
    public static List<LibrarySource> order(List<LibrarySource> sources, long now) {
        List<LibrarySource> good = new ArrayList<>();
        List<LibrarySource> bad = new ArrayList<>();
        for (LibrarySource source : sources) {
            Long at = FAILED_AT.get(source.getUrl());
            (at != null && now - at < MEMORY_MILLIS ? bad : good).add(source);
        }
        good.addAll(bad);
        return good;
    }

    public static void forgetAll() {
        FAILED_AT.clear();
    }
}
