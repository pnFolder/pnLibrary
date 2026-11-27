package org.pnfolder.pnlibrary.updates.databases.postgres;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.pnfolder.pnlibrary.updates.databases.dump.TableMask;

/** Reads the sequences of the current schema and writes them, and their positions, as statements. */
final class PostgresSequences {
    private final Connection connection;

    PostgresSequences(Connection connection) {
        this.connection = connection;
    }

    List<String> statements(List<String> only) throws SQLException {
        Set<String> wanted = new HashSet<>(owned(
                "SELECT s.relname, t.relname FROM pg_class s JOIN pg_namespace n ON n.oid = s.relnamespace\n"
                        + "LEFT JOIN pg_depend d ON d.objid = s.oid AND d.deptype = 'a' LEFT JOIN pg_class t ON t.oid = d.refobjid\n"
                        + "WHERE s.relkind = 'S' AND n.nspname = current_schema()\n"
                        + "  AND NOT EXISTS (SELECT 1 FROM pg_depend i WHERE i.objid = s.oid AND i.deptype = 'i') ORDER BY 1",
                only));
        List<String> statements = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT sequencename, data_type::text, start_value, increment_by, min_value, max_value, cycle "
                             + "FROM pg_sequences WHERE schemaname = current_schema() ORDER BY 1")) {
            while (rows.next()) {
                if (wanted.contains(rows.getString(1))) {
                    statements.add(PostgresSyntax.createSequence(rows.getString(1), rows.getString(2), rows.getLong(3),
                            rows.getLong(4), rows.getLong(5), rows.getLong(6), rows.getBoolean(7)));
                }
            }
        }
        return statements;
    }

    List<String> positions(List<String> only) throws SQLException {
        Set<String> owners = new HashSet<>(owned(
                "SELECT s.relname, t.relname FROM pg_class s JOIN pg_namespace n ON n.oid = s.relnamespace\n"
                        + "LEFT JOIN pg_depend d ON d.objid = s.oid AND d.deptype IN ('a', 'i') LEFT JOIN pg_class t ON t.oid = d.refobjid\n"
                        + "WHERE s.relkind = 'S' AND n.nspname = current_schema()",
                only));
        List<String> statements = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT sequencename, last_value FROM pg_sequences WHERE schemaname = current_schema() AND last_value IS NOT NULL")) {
            while (rows.next()) {
                String name = rows.getString(1);
                long value = rows.getLong(2);
                if (owners.contains(name)) {
                    statements.add("SELECT setval(" + PostgresSyntax.literal(PostgresSyntax.identifier(name)) + ", "
                            + value + ", true)");
                }
            }
        }
        return statements;
    }

    private List<String> owned(String sql, List<String> only) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                String owner = rows.getString(2);
                if (owner != null && TableMask.matches(only, owner) || only.isEmpty()) {
                    names.add(rows.getString(1));
                }
            }
        }
        return names;
    }
}
