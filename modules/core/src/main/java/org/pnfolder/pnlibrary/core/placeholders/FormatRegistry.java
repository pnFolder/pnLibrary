package org.pnfolder.pnlibrary.core.placeholders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.registry.Visibility;

/**
 * The formats of the library. The nearest format of a name wins: the own module, the own plugin, other plugins, and
 * last the built-in ones. {@code onConflict} hears when two modules bring a format of the same name for the same type.
 */
public final class FormatRegistry {
    private static final int BUILT_IN = 3;

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<FormatDef>> byName =
            new ConcurrentHashMap<String, CopyOnWriteArrayList<FormatDef>>();
    private final Consumer<String> onConflict;

    public FormatRegistry(@NotNull Consumer<String> onConflict) {
        this.onConflict = onConflict;
    }

    public void add(@NotNull FormatDef format) {
        CopyOnWriteArrayList<FormatDef> list = byName.get(format.getName().toLowerCase(Locale.ROOT));
        if (list == null) {
            list = new CopyOnWriteArrayList<FormatDef>();
            CopyOnWriteArrayList<FormatDef> raced = byName.putIfAbsent(format.getName().toLowerCase(Locale.ROOT), list);
            if (raced != null) {
                list = raced;
            }
        }
        if (!format.getOwner().isEmpty()) {
            warnAboutClash(list, format);
        }
        list.add(format);
    }

    private void warnAboutClash(List<FormatDef> list, FormatDef format) {
        for (FormatDef other : list) {
            boolean clash = !other.getOwner().isEmpty() && !other.getOwner().equals(format.getOwner())
                    && other.getInput() == format.getInput();
            if (clash) {
                onConflict.accept("Format '" + format.getName() + "' of " + format.getOwner() + " and of " + other.getOwner()
                        + " fit the same value; the nearer one is used");
                return;
            }
        }
    }

    public void removeOwner(@NotNull String owner) {
        for (CopyOnWriteArrayList<FormatDef> list : byName.values()) {
            for (FormatDef format : list) {
                if (format.getOwner().equals(owner)) {
                    list.remove(format);
                }
            }
        }
    }

    /** The format called {@code name} that {@code consumer} may use and that accepts {@code value}; the nearest one. */
    public @Nullable FormatDef find(@NotNull String name, @NotNull Object value, @Nullable String consumer) {
        return find(name, value, consumer, null);
    }

    /** Like {@link #find(String, Object, String)}, with the owner written in front of the format name. */
    public @Nullable FormatDef find(@NotNull String name, @NotNull Object value, @Nullable String consumer, @Nullable String written) {
        List<FormatDef> list = byName.get(name.toLowerCase(Locale.ROOT));
        if (list == null) {
            return null;
        }
        FormatDef best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (FormatDef format : list) {
            if (!format.getInput().isInstance(value) || !Ownership.ownerFits(written, format.getOwner(), consumer)) {
                continue;
            }
            Integer distance = format.getOwner().isEmpty()
                    ? Integer.valueOf(BUILT_IN)
                    : Ownership.nearness(format.getOwner(), Visibility.ALL, consumer);
            if (distance != null && distance < bestDistance) {
                best = format;
                bestDistance = distance;
            }
        }
        return best;
    }

    public @NotNull List<FormatDef> all() {
        List<FormatDef> all = new ArrayList<FormatDef>();
        for (List<FormatDef> list : byName.values()) {
            all.addAll(list);
        }
        return all;
    }

    public @NotNull List<String> names() {
        List<String> names = new ArrayList<String>();
        for (java.util.Map.Entry<String, CopyOnWriteArrayList<FormatDef>> entry : byName.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                names.add(entry.getKey());
            }
        }
        Collections.sort(names);
        return names;
    }
}
