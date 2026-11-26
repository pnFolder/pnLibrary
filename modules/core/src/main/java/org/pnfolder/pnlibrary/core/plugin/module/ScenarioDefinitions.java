package org.pnfolder.pnlibrary.core.plugin.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.registry.RegistryEntry;
import org.pnfolder.pnlibrary.api.registry.Visibility;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;
import org.pnfolder.pnlibrary.api.scenario.actions.DefinitionConflict;
import org.pnfolder.pnlibrary.api.scenario.actions.ScenarioLookup;
import org.pnfolder.pnlibrary.api.scenario.expression.ExpressionFunction;
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement;
import org.pnfolder.pnlibrary.core.placeholders.Ownership;
import org.pnfolder.pnlibrary.core.scenario.ScenarioDefinitionInfo;

/**
 * Named actions, requirements and expression functions of the whole library. Every definition has an owner
 * ({@code plugin/module}) and a {@link Visibility}; {@link #view} gives the lookup of one module, which finds the nearest one
 * of a name. A name that two modules define while they can see each other is reported to the {@code warn} of the later one.
 */
public final class ScenarioDefinitions {
    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");
    private static final Consumer<String> SILENT = message -> { };

    private static final class Entry<T> {
        private final T value;
        private final String owner;
        private final Visibility visibility;

        private Entry(T value, String owner, Visibility visibility) {
            this.value = value;
            this.owner = owner;
            this.visibility = visibility;
        }
    }

    private final Map<String, List<Entry<Action>>> actions = new HashMap<String, List<Entry<Action>>>();
    private final Map<String, List<Entry<Requirement>>> requirements = new HashMap<String, List<Entry<Requirement>>>();
    private final Map<String, List<Entry<ExpressionFunction>>> functions = new HashMap<String, List<Entry<ExpressionFunction>>>();

    public @NotNull AutoCloseable defineAction(@NotNull String name, @NotNull Action value, @NotNull String owner,
                                               @NotNull DefinitionConflict conflict) {
        return defineAction(name, value, owner, conflict, Visibility.ALL, SILENT);
    }

    public @NotNull AutoCloseable defineAction(@NotNull String name, @NotNull Action value, @NotNull String owner,
                                               @NotNull DefinitionConflict conflict, @NotNull Visibility visibility,
                                               @NotNull Consumer<String> warn) {
        return define(actions, "action", name, value, owner, conflict, visibility, warn);
    }

    public @NotNull AutoCloseable defineRequirement(@NotNull String name, @NotNull Requirement value, @NotNull String owner,
                                                    @NotNull DefinitionConflict conflict) {
        return defineRequirement(name, value, owner, conflict, Visibility.ALL, SILENT);
    }

    public @NotNull AutoCloseable defineRequirement(@NotNull String name, @NotNull Requirement value, @NotNull String owner,
                                                    @NotNull DefinitionConflict conflict, @NotNull Visibility visibility,
                                                    @NotNull Consumer<String> warn) {
        return define(requirements, "requirement", name, value, owner, conflict, visibility, warn);
    }

    public @NotNull AutoCloseable defineFunction(@NotNull String name, @NotNull ExpressionFunction value, @NotNull String owner,
                                                 @NotNull DefinitionConflict conflict) {
        return defineFunction(name, value, owner, conflict, Visibility.ALL, SILENT);
    }

    public @NotNull AutoCloseable defineFunction(@NotNull String name, @NotNull ExpressionFunction value, @NotNull String owner,
                                                 @NotNull DefinitionConflict conflict, @NotNull Visibility visibility,
                                                 @NotNull Consumer<String> warn) {
        return define(functions, "function", name, value, owner, conflict, visibility, warn);
    }

    /** Removes everything {@code owner} defined; called when its module closes. */
    public synchronized void removeOwner(@NotNull String owner) {
        removeOwner(actions, owner);
        removeOwner(requirements, owner);
        removeOwner(functions, owner);
    }

    /** What the module {@code consumer} ({@code plugin/module}) can see, nearest first. */
    public @NotNull ScenarioLookup view(@NotNull final String consumer) {
        return new ScenarioLookup() {
            @Override
            public @Nullable Action action(@NotNull String name) {
                return find(actions, name, consumer);
            }

            @Override
            public @Nullable Requirement requirement(@NotNull String name) {
                return find(requirements, name, consumer);
            }

            @Override
            public @Nullable ExpressionFunction function(@NotNull String name) {
                return find(functions, name, consumer);
            }
        };
    }

    public @NotNull List<RegistryEntry<Action>> actionEntries(@NotNull String consumer) {
        return visibleTo(actions, consumer);
    }

    public @NotNull List<RegistryEntry<Requirement>> requirementEntries(@NotNull String consumer) {
        return visibleTo(requirements, consumer);
    }

    public @NotNull List<RegistryEntry<ExpressionFunction>> functionEntries(@NotNull String consumer) {
        return visibleTo(functions, consumer);
    }

    /** The overview for commands and menus: every definition with its owner and who may use it. */
    public synchronized @NotNull List<ScenarioDefinitionInfo> infos() {
        List<ScenarioDefinitionInfo> infos = new ArrayList<ScenarioDefinitionInfo>();
        collectInfos(infos, "action", actions);
        collectInfos(infos, "requirement", requirements);
        collectInfos(infos, "function", functions);
        Collections.sort(infos, Comparator.comparing(ScenarioDefinitionInfo::getKind)
                .thenComparing(ScenarioDefinitionInfo::getName).thenComparing(ScenarioDefinitionInfo::getOwner));
        return infos;
    }

    private static <T> void collectInfos(List<ScenarioDefinitionInfo> into, String kind, Map<String, List<Entry<T>>> map) {
        for (Map.Entry<String, List<Entry<T>>> named : map.entrySet()) {
            for (Entry<T> entry : named.getValue()) {
                into.add(new ScenarioDefinitionInfo(kind, named.getKey(), entry.owner, entry.visibility.name().toLowerCase(Locale.ROOT)));
            }
        }
    }

    private static <T> void removeOwner(Map<String, List<Entry<T>>> map, String owner) {
        for (List<Entry<T>> list : map.values()) {
            for (Iterator<Entry<T>> it = list.iterator(); it.hasNext(); ) {
                if (it.next().owner.equals(owner)) {
                    it.remove();
                }
            }
        }
    }

    private synchronized <T> List<RegistryEntry<T>> visibleTo(Map<String, List<Entry<T>>> map, String consumer) {
        List<RegistryEntry<T>> visible = new ArrayList<RegistryEntry<T>>();
        for (Map.Entry<String, List<Entry<T>>> named : map.entrySet()) {
            for (Entry<T> entry : named.getValue()) {
                if (Ownership.nearness(entry.owner, entry.visibility, consumer) != null) {
                    visible.add(new RegistryEntry<T>(named.getKey(), entry.value, entry.owner, entry.visibility));
                }
            }
        }
        Collections.sort(visible, Comparator.comparing((RegistryEntry<T> entry) -> entry.getKey())
                .thenComparing(entry -> entry.getOwner()));
        return visible;
    }

    private synchronized <T> T find(Map<String, List<Entry<T>>> map, String written, String consumer) {
        int at = written.indexOf('@');
        String owner = at > 0 ? written.substring(0, at).trim().toLowerCase() : null;
        String name = (at > 0 ? written.substring(at + 1) : written).trim().toLowerCase();
        List<Entry<T>> candidates = map.get(name);
        if (candidates == null) {
            return null;
        }
        T best = null;
        Integer bestNearness = null;
        for (Entry<T> entry : candidates) {
            if (!Ownership.ownerFits(owner, entry.owner, consumer)) {
                continue;
            }
            Integer nearness = Ownership.nearness(entry.owner, entry.visibility, consumer);
            if (nearness != null && (bestNearness == null || nearness < bestNearness)) {
                best = entry.value;
                bestNearness = nearness;
            }
        }
        return best;
    }

    private synchronized <T> AutoCloseable define(Map<String, List<Entry<T>>> map, String kind, String rawName, T value,
                                                  String owner, DefinitionConflict conflict, Visibility visibility,
                                                  Consumer<String> warn) {
        String name = rawName.trim().toLowerCase();
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("A " + kind
                    + " name may contain only letters, digits, dots, hyphens and underscores, but was '" + rawName + "'");
        }
        List<Entry<T>> list = listFor(map, name);
        Entry<T> mine = ownedBy(list, owner);
        if (mine != null) {
            if (conflict == DefinitionConflict.KEEP) {
                return () -> { };
            }
            if (conflict == DefinitionConflict.FAIL) {
                throw new IllegalArgumentException("The " + kind + " '" + name + "' is already defined by " + owner);
            }
            list.remove(mine);
        }
        warnAboutNeighbour(list, kind, name, owner, visibility, warn);
        final Entry<T> entry = new Entry<T>(value, owner, visibility);
        final List<Entry<T>> target = list;
        target.add(entry);
        return () -> {
            synchronized (this) {
                target.remove(entry);
            }
        };
    }

    private static <T> List<Entry<T>> listFor(Map<String, List<Entry<T>>> map, String name) {
        List<Entry<T>> list = map.get(name);
        if (list == null) {
            list = new ArrayList<Entry<T>>();
            map.put(name, list);
        }
        return list;
    }

    private static <T> Entry<T> ownedBy(List<Entry<T>> list, String owner) {
        for (Entry<T> entry : list) {
            if (entry.owner.equals(owner)) {
                return entry;
            }
        }
        return null;
    }

    private static <T> void warnAboutNeighbour(List<Entry<T>> list, String kind, String name, String owner,
                                               Visibility visibility, Consumer<String> warn) {
        for (Entry<T> other : list) {
            boolean sees = Ownership.nearness(other.owner, other.visibility, owner) != null
                    || Ownership.nearness(owner, visibility, other.owner) != null;
            if (!other.owner.equals(owner) && sees) {
                warn.accept("The " + kind + " '" + name + "' is defined by " + owner + " and by " + other.owner
                        + "; the nearer one is used");
                return;
            }
        }
    }
}
