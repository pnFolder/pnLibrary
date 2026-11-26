package org.pnfolder.pnlibrary.core.placeholders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterRegistry;
import org.pnfolder.pnlibrary.api.placeholders.reply.PublishedAnswers;

/** The outside placeholder systems, such as PlaceholderAPI, and the prefixes of the library shown to each of them. */
final class AdapterPublications implements PlaceholderAdapterRegistry {
    private final ConcurrentHashMap<String, PlaceholderAdapter> adapters = new ConcurrentHashMap<String, PlaceholderAdapter>();
    private final ConcurrentHashMap<String, AutoCloseable> publications = new ConcurrentHashMap<String, AutoCloseable>();
    private final Function<String, PublishedAnswers> answers;
    private final Consumer<String> report;
    private final Supplier<Set<String>> wanted;

    AdapterPublications(Function<String, PublishedAnswers> answers, Supplier<Set<String>> wanted, Consumer<String> report) {
        this.answers = answers;
        this.wanted = wanted;
        this.report = report;
    }

    @Override
    public @NotNull AutoCloseable register(@NotNull final PlaceholderAdapter adapter) {
        final String key = adapter.getId().toLowerCase(Locale.ROOT);
        if (adapters.putIfAbsent(key, adapter) != null) {
            throw new IllegalArgumentException("Placeholder adapter " + adapter.getId() + " is already registered");
        }
        reconcile();
        return new AutoCloseable() {
            @Override
            public void close() {
                if (!adapters.remove(key, adapter)) {
                    return;
                }
                for (String published : new ArrayList<String>(publications.keySet())) {
                    if (published.startsWith(key + "/")) {
                        closeQuietly(publications.remove(published));
                    }
                }
                if (adapter instanceof AutoCloseable) {
                    closeQuietly((AutoCloseable) adapter);
                }
            }
        };
    }

    @Override
    public @Nullable PlaceholderAdapter get(@NotNull String id) {
        return adapters.get(id.toLowerCase(Locale.ROOT));
    }

    @Override
    public @NotNull List<PlaceholderAdapter> all() {
        List<PlaceholderAdapter> all = new ArrayList<PlaceholderAdapter>(adapters.values());
        Collections.sort(all, (a, b) -> a.getId().compareTo(b.getId()));
        return all;
    }

    /** Shows every prefix that asks for it to every adapter that is there, and withdraws the prefixes nobody asks for. */
    synchronized void reconcile() {
        Set<String> asked = wanted.get();
        for (Map.Entry<String, PlaceholderAdapter> adapter : adapters.entrySet()) {
            for (String prefix : asked) {
                publish(adapter.getKey(), adapter.getValue(), prefix);
            }
        }
        for (String key : new ArrayList<String>(publications.keySet())) {
            if (!asked.contains(key.substring(key.indexOf('/') + 1))) {
                closeQuietly(publications.remove(key));
            }
        }
    }

    private void publish(String id, PlaceholderAdapter adapter, String prefix) {
        String key = id + "/" + prefix;
        if (publications.containsKey(key) || !adapter.isAvailable()) {
            return;
        }
        try {
            publications.put(key, adapter.publish(prefix, answers.apply(prefix)));
        } catch (Exception failure) {
            report.accept("Could not show '" + prefix + "' to " + adapter.getId() + ": " + Failures.describe(failure));
        }
    }

    private void closeQuietly(@Nullable AutoCloseable handle) {
        if (handle == null) {
            return;
        }
        try {
            handle.close();
        } catch (Exception failure) {
            report.accept("Closing failed: " + Failures.describe(failure));
        }
    }

    /** What the adapters know about {@code path}, the first answer; {@code null} when none knows. */
    @Nullable Object fromAdapters(@Nullable UUID playerId, String path) {
        for (PlaceholderAdapter adapter : all()) {
            if (!adapter.isAvailable()) {
                continue;
            }
            try {
                Object value = adapter.resolve(playerId, path);
                if (value != null) {
                    return value;
                }
            } catch (Exception failure) {
                report.accept("Placeholder {" + path + "} failed in " + adapter.getId() + ": " + Failures.describe(failure));
            }
        }
        return null;
    }

    /** Lets every available adapter fill its {@code %tokens%} into {@code text}. */
    String renderOutside(String text, @Nullable UUID playerId) {
        if (text.indexOf('%') < 0) {
            return text;
        }
        String current = text;
        for (PlaceholderAdapter adapter : all()) {
            if (!adapter.isAvailable()) {
                continue;
            }
            try {
                current = adapter.render(playerId, current);
            } catch (Exception failure) {
                report.accept(adapter.getId() + " failed to render a text: " + Failures.describe(failure));
            }
        }
        return current;
    }
}
