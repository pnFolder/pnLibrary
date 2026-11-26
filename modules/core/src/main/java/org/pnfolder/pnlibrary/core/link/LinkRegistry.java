package org.pnfolder.pnlibrary.core.link;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.core.link.report.LinkReport;

/** The newest report of every server that answered over the link; a newer report from the same name replaces the old one. */
public final class LinkRegistry {
    private final ConcurrentHashMap<String, LinkEntry> entries = new ConcurrentHashMap<>();

    /** Stores {@code report} as the latest of {@code name}. */
    public void accept(@NotNull String name, @NotNull LinkReport report) {
        accept(name, report, Instant.now());
    }

    /** Stores {@code report} as the latest of {@code name}, received at {@code now}. */
    public void accept(@NotNull String name, @NotNull LinkReport report, @NotNull Instant now) {
        entries.put(name, new LinkEntry(name, report, now));
    }

    /** Every known server, sorted by name without regard to case. */
    public @NotNull List<LinkEntry> all() {
        List<LinkEntry> all = new ArrayList<>(entries.values());
        Collections.sort(all, (a, b) -> new LowerCaseOrder().compare(a.getName(), b.getName()));
        return all;
    }

    /** The server called {@code name}, ignoring case, or {@code null}. */
    public @Nullable LinkEntry get(@NotNull String name) {
        for (LinkEntry entry : entries.values()) {
            if (entry.getName().equalsIgnoreCase(name)) {
                return entry;
            }
        }
        return null;
    }

    /** Forgets every server. */
    public void clear() {
        entries.clear();
    }
}
