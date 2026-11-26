package org.pnfolder.pnlibrary.currency.storage;

import java.sql.Connection;
import java.sql.SQLException;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.lang.Failures;

/** Transaction settings of a borrowed connection, put back before it returns to the caller's pool. */
final class ConnectionSettings {
    private final boolean autoCommit;
    private final int isolation;

    private ConnectionSettings(boolean autoCommit, int isolation) {
        this.autoCommit = autoCommit;
        this.isolation = isolation;
    }

    /** Reads the current settings of {@code connection}. */
    @NotNull
    static ConnectionSettings capture(@NotNull Connection connection) throws SQLException {
        return new ConnectionSettings(connection.getAutoCommit(), connection.getTransactionIsolation());
    }

    /** Reapplies the captured settings; failures are ignored because the connection is being returned. */
    void restore(@NotNull Connection connection) {
        Failures.runCatchingNonFatal(() -> {
            connection.setAutoCommit(autoCommit);
            return null;
        });
        Failures.runCatchingNonFatal(() -> {
            connection.setTransactionIsolation(isolation);
            return null;
        });
    }
}
