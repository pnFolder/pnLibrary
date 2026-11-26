package org.pnfolder.pnlibrary.updates.databases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;

class SqlScriptRestoreTest {
    @TempDir
    Path directory;

    private final Statement statement = mock(Statement.class);
    private final Connection connection = mock(Connection.class);

    @BeforeEach
    void connect() throws SQLException {
        when(connection.createStatement()).thenReturn(statement);
    }

    private Path script(String... lines) throws IOException {
        Path file = directory.resolve("copy.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write(String.join("\n", lines).getBytes());
        }
        return file;
    }

    @Test
    void anAtomicRestoreCommitsAfterTheLastStatement() throws Exception {
        SqlScriptRestore.atomic("-- h").read(connection, script("-- h", "A", "B"));
        InOrder order = inOrder(connection, statement);
        order.verify(connection).setAutoCommit(false);
        order.verify(statement).execute("A");
        order.verify(statement).execute("B");
        order.verify(connection).commit();
    }

    @Test
    void anAtomicRestoreRollsBackWhenAStatementFails() throws Exception {
        when(statement.execute("B")).thenThrow(new SQLException("boom"));
        Path script = script("-- h", "A", "B");
        assertThrows(SQLException.class, () -> SqlScriptRestore.atomic("-- h").read(connection, script));
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test
    void aPlainRestoreUsesAutocommitAndAForeignFileIsRefused() throws Exception {
        new SqlScriptRestore("-- h").read(connection, script("-- h", "A"));
        verify(connection).setAutoCommit(true);
        verify(connection, never()).commit();
        Path foreign = script("-- other", "A");
        assertThrows(IllegalArgumentException.class, () -> new SqlScriptRestore("-- h").read(connection, foreign));
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }
}
