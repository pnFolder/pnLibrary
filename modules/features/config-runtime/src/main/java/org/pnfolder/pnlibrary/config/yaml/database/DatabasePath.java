package org.pnfolder.pnlibrary.config.yaml.database;

import java.util.Objects;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.config.DatabaseKind;
import org.pnfolder.pnlibrary.api.config.DatabaseRole;

/** A section holding database connection details: where it is in the file, what kind it is and what it is called. */
public final class DatabasePath {
    private final String path;
    private final DatabaseKind kind;
    private final String name;
    private final Map<DatabaseRole, String> keys;
    private final List<String> tables;
    private final List<String> tableKeys;

    /** Creates a section without role keys or tables. */
    public DatabasePath(@NotNull String path, @NotNull DatabaseKind kind, @NotNull String name) {
        this(path, kind, name, Collections.emptyMap(), Collections.emptyList(),
                Collections.emptyList());
    }

    /**
     * Creates a section.
     *
     * @param path dotted path of the section; empty when the connection fields sit at the top of the file
     * @param kind database server type
     * @param name short name from {@code @BackupDatabase}
     * @param keys YAML key of the field that holds each part of the connection
     * @param tables table or collection names written in the annotation
     * @param tableKeys YAML keys of the fields that hold table names
     */
    public DatabasePath(@NotNull String path, @NotNull DatabaseKind kind, @NotNull String name,
                        @NotNull Map<DatabaseRole, String> keys, @NotNull List<String> tables,
                        @NotNull List<String> tableKeys) {
        this.path = Objects.requireNonNull(path, "path");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.name = Objects.requireNonNull(name, "name");
        this.keys = keys;
        this.tables = tables;
        this.tableKeys = tableKeys;
    }

    /** Dotted path of the section; empty when the connection fields sit at the top of the file. */
    @NotNull
    public String getPath() {
        return path;
    }

    /** Database server type. */
    @NotNull
    public DatabaseKind getKind() {
        return kind;
    }

    /** Short name from {@code @BackupDatabase}. */
    @NotNull
    public String getName() {
        return name;
    }

    /** YAML key of the field that holds each part of the connection. */
    @NotNull
    public Map<DatabaseRole, String> getKeys() {
        return keys;
    }

    /** Table or collection names written in the annotation. */
    @NotNull
    public List<String> getTables() {
        return tables;
    }

    /** YAML keys of the fields that hold table names. */
    @NotNull
    public List<String> getTableKeys() {
        return tableKeys;
    }
}
