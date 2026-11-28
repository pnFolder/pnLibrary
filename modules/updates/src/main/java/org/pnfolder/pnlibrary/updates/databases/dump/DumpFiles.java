package org.pnfolder.pnlibrary.updates.databases.dump;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.zip.GZIPOutputStream;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.updates.history.backup.PrivateFiles;

/** Opens the compressed file of a database copy. */
public final class DumpFiles {
    private DumpFiles() {
    }

    /** Opens the compressed copy at {@code target}, writes {@code header} as its first line and lets {@code body} write the statements. */
    public static void write(@NotNull Path target, @NotNull String header, @NotNull DumpBody body) throws IOException, SQLException {
        try (BufferedWriter out = new BufferedWriter(
                new OutputStreamWriter(new GZIPOutputStream(PrivateFiles.output(target)), StandardCharsets.UTF_8))) {
            out.write(header + "\n");
            body.write(out);
        }
    }
}
