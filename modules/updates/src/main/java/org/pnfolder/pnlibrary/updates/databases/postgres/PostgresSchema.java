package org.pnfolder.pnlibrary.updates.databases.postgres;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/** Reads the shape of the tables of the current schema from the system catalogs and writes it as statements. */
public final class PostgresSchema {
    private static final RowMapper<String> FIRST_TEXT = new RowMapper<String>() {
        @Override
        public String map(ResultSet rows) throws SQLException {
            return rows.getString(1);
        }
    };

    private static final RowMapper<String[]> TWO_TEXTS = new RowMapper<String[]>() {
        @Override
        public String[] map(ResultSet rows) throws SQLException {
            return new String[] {rows.getString(1), rows.getString(2)};
        }
    };

    private final Connection connection;
    private final PostgresSequences sequences;

    public PostgresSchema(@NotNull Connection connection) {
        this.connection = connection;
        this.sequences = new PostgresSequences(connection);
    }

    /** A table column; generated columns are computed by the server and are not copied. */
    public static final class Column {
        private final String name;
        private final boolean generated;

        /**
         * @param name column name
         * @param generated true for a stored generated column
         */
        public Column(@NotNull String name, boolean generated) {
            this.name = name;
            this.generated = generated;
        }

        @NotNull
        public String getName() {
            return name;
        }

        public boolean isGenerated() {
            return generated;
        }
    }

    /** Names of the base tables of the current schema. */
    @NotNull
    public List<String> tables() throws SQLException {
        return query("SELECT table_name FROM information_schema.tables WHERE table_schema = current_schema() "
                + "AND table_type = 'BASE TABLE' ORDER BY 1", FIRST_TEXT);
    }

    /**
     * {@code CREATE SEQUENCE} for sequences that belong to no identity column; identity columns bring their own.
     * {@code only} keeps those owned by the listed tables.
     */
    @NotNull
    public List<String> sequences(@NotNull List<String> only) throws SQLException {
        return sequences.statements(only);
    }

    /** {@code setval} statements that give every sequence its current position back. */
    @NotNull
    public List<String> sequencePositions(@NotNull List<String> only) throws SQLException {
        return sequences.positions(only);
    }

    /** Statements that create the enum types the columns of {@code tables} use; they run before the tables. */
    @NotNull
    public List<String> enumTypes(@NotNull List<String> tables) throws SQLException {
        if (tables.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map.Entry<Long, String>> types = new ArrayList<Map.Entry<Long, String>>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT DISTINCT ty.oid, ty.typname FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid\n"
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace JOIN pg_type col ON col.oid = a.atttypid\n"
                        + "JOIN pg_type ty ON ty.oid = CASE WHEN col.typcategory = 'A' THEN col.typelem ELSE col.oid END\n"
                        + "WHERE ty.typtype = 'e' AND ty.typnamespace = n.oid AND n.nspname = current_schema() AND c.relkind = 'r'\n"
                        + "  AND a.attnum > 0 AND NOT a.attisdropped AND c.relname = ANY (?) ORDER BY ty.typname")) {
            statement.setArray(1, connection.createArrayOf("text", tables.toArray()));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    types.add(new AbstractMap.SimpleEntry<Long, String>(rows.getLong(1), rows.getString(2)));
                }
            }
        }
        List<String> statements = new ArrayList<String>();
        for (Map.Entry<Long, String> type : types) {
            statements.add(PostgresSyntax.createEnum(type.getValue(), enumLabels(type.getKey())));
        }
        return statements;
    }

    private List<String> enumLabels(long oid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT enumlabel FROM pg_enum WHERE enumtypid = ? ORDER BY enumsortorder")) {
            statement.setLong(1, oid);
            return read(statement, FIRST_TEXT);
        }
    }

    /**
     * Foreign keys of tables outside {@code tables} that point at them. {@code DROP TABLE ... CASCADE} removes these, so they are
     * added back as {@code NOT VALID}: the restored rows may differ from what those tables hold now, and a check must not undo the restore.
     */
    @NotNull
    public List<String> externalForeignKeys(@NotNull List<String> tables) throws SQLException {
        if (tables.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> keys = new ArrayList<String>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT cl.relname, c.conname, pg_get_constraintdef(c.oid) FROM pg_constraint c\n"
                        + "JOIN pg_class cl ON cl.oid = c.conrelid JOIN pg_namespace n ON n.oid = cl.relnamespace\n"
                        + "JOIN pg_class ref ON ref.oid = c.confrelid\n"
                        + "WHERE c.contype = 'f' AND n.nspname = current_schema() AND ref.relname = ANY (?) "
                        + "AND NOT (cl.relname = ANY (?))\nORDER BY 1, 2")) {
            java.sql.Array names = connection.createArrayOf("text", tables.toArray());
            statement.setArray(1, names);
            statement.setArray(2, names);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    keys.add(externalKey(rows.getString(1), rows.getString(2), rows.getString(3)));
                }
            }
        }
        return keys;
    }

    private static String externalKey(String table, String name, String definition) {
        String notValid = definition.endsWith(" NOT VALID") ? "" : " NOT VALID";
        return "ALTER TABLE " + PostgresSyntax.identifier(table) + " ADD CONSTRAINT " + PostgresSyntax.identifier(name)
                + " " + definition + notValid;
    }

    /** The columns of {@code table} in order. */
    @NotNull
    public List<Column> columns(@NotNull String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT a.attname, a.attgenerated <> '' FROM pg_attribute a WHERE a.attrelid = ?::regclass "
                        + "AND a.attnum > 0 AND NOT a.attisdropped ORDER BY a.attnum")) {
            statement.setString(1, PostgresSyntax.identifier(table));
            return read(statement, new RowMapper<Column>() {
                @Override
                public Column map(ResultSet rows) throws SQLException {
                    return new Column(rows.getString(1), rows.getBoolean(2));
                }
            });
        }
    }

    /** {@code CREATE TABLE} with columns, defaults and the primary key, unique and check constraints; foreign keys come later. */
    @NotNull
    public String createTable(@NotNull String table) throws SQLException {
        String regclass = PostgresSyntax.identifier(table);
        List<String> parts;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT a.attname, format_type(a.atttypid, a.atttypmod), a.attnotnull, pg_get_expr(d.adbin, d.adrelid), "
                        + "a.attidentity, a.attgenerated\n"
                        + "FROM pg_attribute a LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum\n"
                        + "WHERE a.attrelid = ?::regclass AND a.attnum > 0 AND NOT a.attisdropped ORDER BY a.attnum")) {
            statement.setString(1, regclass);
            parts = read(statement, new RowMapper<String>() {
                @Override
                public String map(ResultSet rows) throws SQLException {
                    return columnDefinition(rows);
                }
            });
        }
        for (String[] constraint : constraints(regclass, "contype <> 'f'")) {
            parts.add("CONSTRAINT " + PostgresSyntax.identifier(constraint[0]) + " " + constraint[1]);
        }
        return "CREATE TABLE " + regclass + " (" + String.join(", ", parts) + ")";
    }

    private static String columnDefinition(ResultSet rows) throws SQLException {
        StringBuilder definition = new StringBuilder(PostgresSyntax.identifier(rows.getString(1))).append(' ')
                .append(rows.getString(2));
        String defaultValue = rows.getString(4);
        if ("s".equals(rows.getString(6))) {
            definition.append(" GENERATED ALWAYS AS (").append(defaultValue).append(") STORED");
        } else if ("a".equals(rows.getString(5))) {
            definition.append(" GENERATED ALWAYS AS IDENTITY");
        } else if ("d".equals(rows.getString(5))) {
            definition.append(" GENERATED BY DEFAULT AS IDENTITY");
        } else if (defaultValue != null) {
            definition.append(" DEFAULT ").append(defaultValue);
        }
        if (rows.getBoolean(3)) {
            definition.append(" NOT NULL");
        }
        return definition.toString();
    }

    /** {@code ALTER TABLE} statements that add the foreign keys of {@code table}; they run after every table exists. */
    @NotNull
    public List<String> foreignKeys(@NotNull String table) throws SQLException {
        String regclass = PostgresSyntax.identifier(table);
        List<String> keys = new ArrayList<String>();
        for (String[] key : constraints(regclass, "contype = 'f'")) {
            keys.add("ALTER TABLE " + regclass + " ADD CONSTRAINT " + PostgresSyntax.identifier(key[0]) + " " + key[1]);
        }
        return keys;
    }

    /** Indexes that are not behind a constraint, as the server writes them. */
    @NotNull
    public List<String> indexes(@NotNull String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = current_schema() AND tablename = ?\n"
                        + "  AND indexname NOT IN (SELECT conname FROM pg_constraint WHERE conrelid = ?::regclass "
                        + "AND contype IN ('p', 'u', 'x'))")) {
            statement.setString(1, table);
            statement.setString(2, PostgresSyntax.identifier(table));
            return read(statement, FIRST_TEXT);
        }
    }

    private List<String[]> constraints(String regclass, String filter) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = ?::regclass AND "
                        + filter + " ORDER BY conname")) {
            statement.setString(1, regclass);
            return read(statement, TWO_TEXTS);
        }
    }

    private <T> List<T> query(String sql, RowMapper<T> mapper) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            return collect(rows, mapper);
        }
    }

    private static <T> List<T> read(PreparedStatement statement, RowMapper<T> mapper) throws SQLException {
        try (ResultSet rows = statement.executeQuery()) {
            return collect(rows, mapper);
        }
    }

    private static <T> List<T> collect(ResultSet rows, RowMapper<T> mapper) throws SQLException {
        List<T> found = new ArrayList<T>();
        while (rows.next()) {
            found.add(mapper.map(rows));
        }
        return found;
    }
}
