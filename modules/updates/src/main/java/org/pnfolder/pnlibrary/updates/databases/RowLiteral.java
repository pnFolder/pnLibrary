package org.pnfolder.pnlibrary.updates.databases;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Turns one column of the current row into an SQL literal of a dialect. */
public interface RowLiteral {
    String literal(ResultSet rows, int column) throws SQLException;
}
