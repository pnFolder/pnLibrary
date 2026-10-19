package org.pnfolder.pnlibrary.core.updates.engine

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/** Shared confinement checks for transaction targets and configuration snapshots. */
internal object UpdatePaths {
    fun inside(path: Path, allowedRoot: Path): Path {
        val normalized = path.toAbsolutePath().normalize()
        val allowed = allowedRoot.toAbsolutePath().normalize()
        require(normalized != allowed && normalized.startsWith(allowed)) { "Update path escapes its allowed root" }
        var cursor: Path? = normalized
        while (cursor != null && cursor.startsWith(allowed)) {
            require(!Files.isSymbolicLink(cursor)) { "Update paths must not contain symbolic links" }
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && Files.exists(allowed)) {
                require(cursor.toRealPath().startsWith(allowed.toRealPath())) { "Update path resolves outside its allowed root" }
            }
            cursor = cursor.parent
        }
        return normalized
    }
}
