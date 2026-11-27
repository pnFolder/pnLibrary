package org.pnfolder.pnlibrary.updates.history.backup;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import org.jetbrains.annotations.NotNull;

/** Creates copies readable by the server user only: databases and exports hold passwords and player data. */
public final class PrivateFiles {
    private static final boolean POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    private static final FileAttribute<?>[] DIRECTORY_ATTRIBUTES = attributes("rwx------");
    private static final FileAttribute<?>[] FILE_ATTRIBUTES = attributes("rw-------");

    private PrivateFiles() {
    }

    /** Creates {@code directory} and its missing parents with {@code rwx------} where POSIX permissions exist. */
    public static void directories(@NotNull Path directory) throws IOException {
        Files.createDirectories(directory, DIRECTORY_ATTRIBUTES);
    }

    /**
     * Opens {@code file} anew with {@code rw-------}; the mode is set at creation, so the content is never readable
     * by others.
     */
    @NotNull
    public static OutputStream output(@NotNull Path file) throws IOException {
        Files.deleteIfExists(file);
        Files.createFile(file, FILE_ATTRIBUTES);
        return Files.newOutputStream(file);
    }

    private static FileAttribute<?>[] attributes(String mode) {
        if (!POSIX) {
            return new FileAttribute<?>[0];
        }
        return new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(mode))};
    }
}
