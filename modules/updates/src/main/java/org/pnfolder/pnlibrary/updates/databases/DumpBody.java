package org.pnfolder.pnlibrary.updates.databases;

import java.io.BufferedWriter;
import java.io.IOException;
import java.sql.SQLException;

/** Writes the statements of a copy after its header line. */
public interface DumpBody {
    void write(BufferedWriter out) throws IOException, SQLException;
}
