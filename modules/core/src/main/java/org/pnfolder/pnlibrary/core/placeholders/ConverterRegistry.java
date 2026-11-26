package org.pnfolder.pnlibrary.core.placeholders;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter;
import org.pnfolder.pnlibrary.api.registry.Visibility;

/** The converters of argument types, by owner; the one nearest to the asking module is used. */
final class ConverterRegistry {
    private static final class Entry {
        final String owner;
        final Visibility visibility;
        final ArgumentConverter<Object> converter;

        Entry(String owner, Visibility visibility, ArgumentConverter<Object> converter) {
            this.owner = owner;
            this.visibility = visibility;
            this.converter = converter;
        }
    }

    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<Entry>> byType =
            new ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<Entry>>();
    private final Consumer<String> report;

    ConverterRegistry(Consumer<String> report) {
        this.report = report;
    }

    /** Registers {@code converter} for {@code type} and returns what takes it back. */
    @SuppressWarnings("unchecked")
    <T> AutoCloseable add(String owner, Class<T> type, ArgumentConverter<T> converter) {
        final Entry entry = new Entry(owner, Visibility.ALL, (ArgumentConverter<Object>) converter);
        final CopyOnWriteArrayList<Entry> list = listOf(type);
        for (Entry other : list) {
            if (!other.owner.equals(owner)) {
                report.accept(owner + " and " + other.owner + " both read " + type.getSimpleName() + "; the nearer one is used");
                break;
            }
        }
        list.add(entry);
        return new AutoCloseable() {
            @Override
            public void close() {
                list.remove(entry);
            }
        };
    }

    private CopyOnWriteArrayList<Entry> listOf(Class<?> type) {
        CopyOnWriteArrayList<Entry> list = byType.get(type);
        if (list == null) {
            list = new CopyOnWriteArrayList<Entry>();
            CopyOnWriteArrayList<Entry> raced = byType.putIfAbsent(type, list);
            if (raced != null) {
                list = raced;
            }
        }
        return list;
    }

    /** The converter of {@code type} nearest to {@code consumer}, or {@code null}. */
    @Nullable ArgumentConverter<Object> find(@NotNull Class<?> type, @Nullable String consumer) {
        CopyOnWriteArrayList<Entry> list = byType.get(type);
        if (list == null) {
            return null;
        }
        Entry best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Entry entry : list) {
            Integer distance = Ownership.nearness(entry.owner, entry.visibility, consumer);
            if (distance != null && distance < bestDistance) {
                best = entry;
                bestDistance = distance;
            }
        }
        return best == null ? null : best.converter;
    }

    void removeOwner(String owner) {
        for (CopyOnWriteArrayList<Entry> list : byType.values()) {
            for (Entry entry : list) {
                if (entry.owner.equals(owner)) {
                    list.remove(entry);
                }
            }
        }
    }
}
