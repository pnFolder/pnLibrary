package org.pnfolder.pnlibrary.config.yaml;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.config.BackupDatabase;
import org.pnfolder.pnlibrary.api.config.BackupTable;
import org.pnfolder.pnlibrary.api.config.DatabaseField;
import org.pnfolder.pnlibrary.api.config.DatabaseRole;

/** Finds the sections of a configuration class that hold database connections marked {@code @BackupDatabase}. */
final class DatabasePathScanner {
    private final ConfigObjectIntrospector introspector;

    DatabasePathScanner(@NotNull ConfigObjectIntrospector introspector) {
        this.introspector = introspector;
    }

    /** The databases marked {@code @BackupDatabase} in {@code type} (the class may carry several), with the dotted YAML path of each. */
    @NotNull
    List<DatabasePath> scan(@NotNull Class<?> type) {
        List<DatabasePath> paths = new ArrayList<DatabasePath>();
        for (BackupDatabase database : type.getAnnotationsByType(BackupDatabase.class)) {
            paths.add(new DatabasePath("", database.kind(), database.name(), roleKeys(type, database.name()),
                    Collections.unmodifiableList(Arrays.asList(database.tables())), tableKeys(type, database.name())));
        }
        for (Field field : introspector.fields(type)) {
            for (BackupDatabase database : field.getAnnotationsByType(BackupDatabase.class)) {
                paths.add(fieldDatabase(type, field, database));
            }
        }
        return paths;
    }

    private DatabasePath fieldDatabase(Class<?> type, Field field, BackupDatabase database) {
        Class<?> section = introspector.rawClass(field.getGenericType());
        List<String> tables = Arrays.asList(database.tables());
        if (introspector.isScalar(section)) {
            // On the field that holds the database name: the other fields of the class carry the same name.
            Map<DatabaseRole, String> keys = new LinkedHashMap<DatabaseRole, String>(roleKeys(type, database.name()));
            keys.put(DatabaseRole.DATABASE, introspector.key(field));
            return new DatabasePath("", database.kind(), database.name(), keys, tables, tableKeys(type, database.name()));
        }
        return new DatabasePath(introspector.key(field), database.kind(), database.name(), roleKeys(section, database.name()), tables,
                tableKeys(section, database.name()));
    }

    /**
     * The YAML key of each field that says which part of a connection it holds, whatever the field is called. A field
     * with a {@code database} belongs to that database only and wins over a shared field without one.
     */
    private Map<DatabaseRole, String> roleKeys(Class<?> section, String database) {
        List<Field> shared = new ArrayList<Field>();
        List<Field> own = new ArrayList<Field>();
        for (Field field : introspector.fields(section)) {
            DatabaseField annotation = field.getAnnotation(DatabaseField.class);
            if (annotation == null || !(annotation.database().isEmpty() || annotation.database().equals(database))) {
                continue;
            }
            (annotation.database().isEmpty() ? shared : own).add(field);
        }
        Map<DatabaseRole, String> keys = new LinkedHashMap<DatabaseRole, String>();
        for (Field field : shared) {
            keys.put(field.getAnnotation(DatabaseField.class).role(), introspector.key(field));
        }
        for (Field field : own) {
            keys.put(field.getAnnotation(DatabaseField.class).role(), introspector.key(field));
        }
        return keys;
    }

    /** The YAML keys of the fields marked {@code @BackupTable} in {@code section} for {@code database}. */
    private List<String> tableKeys(Class<?> section, String database) {
        List<String> keys = new ArrayList<String>();
        for (Field field : introspector.fields(section)) {
            BackupTable table = field.getAnnotation(BackupTable.class);
            if (table != null && (table.database().isEmpty() || table.database().equals(database))) {
                keys.add(introspector.key(field));
            }
        }
        return keys;
    }
}
