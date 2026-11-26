package org.pnfolder.pnlibrary.updates.databases.postgres;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Turns the current row of a result set into a value. */
interface RowMapper<T> {
    T map(ResultSet rows) throws SQLException;
}
