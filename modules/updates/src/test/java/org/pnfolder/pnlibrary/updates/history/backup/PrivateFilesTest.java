package org.pnfolder.pnlibrary.updates.history.backup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrivateFilesTest {
    @TempDir
    Path directory;

    @Test
    void filesAndFoldersArePrivateAndAnOldFileIsReplaced() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path folder = directory.resolve("a/b");
        PrivateFiles.directories(folder);
        Path file = folder.resolve("x.gz");
        Files.write(file, new byte[5]);
        try (OutputStream output = PrivateFiles.output(file)) {
            output.write(1);
        }
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(folder)));
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
        assertEquals(1, Files.size(file));
    }
}
