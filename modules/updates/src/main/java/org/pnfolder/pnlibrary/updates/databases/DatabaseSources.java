package org.pnfolder.pnlibrary.updates.databases;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.config.ManagedConfigFile;
import org.pnfolder.pnlibrary.config.yaml.database.DatabasePath;

/** The databases of all plugins, filled while plugins register their configuration files. */
public final class DatabaseSources {
    private final ConcurrentMap<String, CopyOnWriteArrayList<DatabaseSource>> sources =
            new ConcurrentHashMap<>();

    /** Remembers the databases of {@code file}; closing the result forgets them again. */
    @Nullable
    public AutoCloseable register(@NotNull ManagedConfigFile file) {
        if (file.getDatabases().isEmpty()) {
            return null;
        }
        final String key = file.getPluginId().getValue().toLowerCase(Locale.ROOT);
        Path path = file.getDirectory().resolve(file.getPath());
        final List<DatabaseSource> added = new ArrayList<>();
        for (DatabasePath database : file.getDatabases()) {
            added.add(new DatabaseSource(key, database.getKind(), database.getName(), path, database.getPath(),
                    database.getKeys(), database.getTables(), database.getTableKeys()));
        }
        CopyOnWriteArrayList<DatabaseSource> list = sources.get(key);
        if (list == null) {
            sources.putIfAbsent(key, new CopyOnWriteArrayList<>());
            list = sources.get(key);
        }
        requireDistinctNames(file, added, list);
        list.addAll(added);
        return new AutoCloseable() {
            @Override
            public void close() {
                CopyOnWriteArrayList<DatabaseSource> current = sources.get(key);
                if (current != null) {
                    current.removeAll(new HashSet<>(added));
                }
            }
        };
    }

    /** The databases registered for {@code plugin}, whatever its letter case. */
    @NotNull
    public List<DatabaseSource> of(@NotNull String plugin) {
        List<DatabaseSource> found = sources.get(plugin.toLowerCase(Locale.ROOT));
        return found != null ? found : Collections.emptyList();
    }

    private static void requireDistinctNames(ManagedConfigFile file, List<DatabaseSource> added, List<DatabaseSource> list) {
        List<String> names = new ArrayList<>();
        for (DatabaseSource source : added) {
            names.add(source.getName());
        }
        for (DatabaseSource source : list) {
            names.add(source.getName());
        }
        if (new HashSet<>(names).size() != names.size()) {
            throw new IllegalArgumentException("У плагина " + file.getPluginId().getValue()
                    + " две базы с одним именем: имя в @BackupDatabase должно быть разным");
        }
    }
}
