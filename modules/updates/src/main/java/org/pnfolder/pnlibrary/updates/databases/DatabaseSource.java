package org.pnfolder.pnlibrary.updates.databases;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.config.DatabaseKind;
import org.pnfolder.pnlibrary.api.config.DatabaseRole;

/** A database a plugin marked with {@code @BackupDatabase}: whose it is, what it is called and where its connection is written. */
public final class DatabaseSource {
    private final String plugin;
    private final DatabaseKind kind;
    private final String name;
    private final Path file;
    private final String path;
    private final Map<DatabaseRole, String> keys;
    private final List<String> tables;
    private final List<String> tableKeys;

    public DatabaseSource(@NotNull String plugin, @NotNull DatabaseKind kind, @NotNull String name, @NotNull Path file,
                          @NotNull String path) {
        this(plugin, kind, name, file, path, Collections.<DatabaseRole, String>emptyMap());
    }

    public DatabaseSource(@NotNull String plugin, @NotNull DatabaseKind kind, @NotNull String name, @NotNull Path file,
                          @NotNull String path, @NotNull Map<DatabaseRole, String> keys) {
        this(plugin, kind, name, file, path, keys, Collections.<String>emptyList(), Collections.<String>emptyList());
    }

    /**
     * @param plugin lowercase id of the plugin that owns the database
     * @param kind database engine
     * @param name name given in {@code @BackupDatabase}, unique within the plugin
     * @param file configuration file that holds the connection
     * @param path dotted path of the connection section inside {@code file}
     * @param keys field names that replace the usual ones, by role
     * @param tables fixed tables or collections to copy, empty for all
     * @param tableKeys configuration fields that hold a table name
     */
    public DatabaseSource(@NotNull String plugin, @NotNull DatabaseKind kind, @NotNull String name, @NotNull Path file,
                          @NotNull String path, @NotNull Map<DatabaseRole, String> keys, @NotNull List<String> tables,
                          @NotNull List<String> tableKeys) {
        this.plugin = plugin;
        this.kind = kind;
        this.name = name;
        this.file = file;
        this.path = path;
        this.keys = keys;
        this.tables = tables;
        this.tableKeys = tableKeys;
    }

    @NotNull
    public String getPlugin() {
        return plugin;
    }

    @NotNull
    public DatabaseKind getKind() {
        return kind;
    }

    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public Path getFile() {
        return file;
    }

    @NotNull
    public String getPath() {
        return path;
    }

    @NotNull
    public Map<DatabaseRole, String> getKeys() {
        return keys;
    }

    @NotNull
    public List<String> getTables() {
        return tables;
    }

    @NotNull
    public List<String> getTableKeys() {
        return tableKeys;
    }
}
