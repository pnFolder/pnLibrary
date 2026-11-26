package org.pnfolder.pnlibrary.updates.engine;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Shared confinement checks for transaction targets and configuration snapshots. */
public final class UpdatePaths {
    private UpdatePaths() {
    }

    /** Returns {@code path} normalized, failing if it escapes {@code allowedRoot}. */
    @NotNull
    public static Path inside(@NotNull Path path, @NotNull Path allowedRoot) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        Path allowed = allowedRoot.toAbsolutePath().normalize();
        require(!normalized.equals(allowed) && normalized.startsWith(allowed), "Update path escapes its allowed root");
        Path cursor = normalized;
        while (cursor != null && cursor.startsWith(allowed)) {
            require(!Files.isSymbolicLink(cursor), "Update paths must not contain symbolic links");
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && Files.exists(allowed)) {
                require(cursor.toRealPath().startsWith(allowed.toRealPath()), "Update path resolves outside its allowed root");
            }
            cursor = cursor.getParent();
        }
        return normalized;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
