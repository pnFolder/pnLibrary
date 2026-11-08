package org.pnfolder.pnlibrary.core.updates.history

import java.io.OutputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermissions

/** Creates copies readable by the server user only: databases and exports hold passwords and player data. */
internal object PrivateFiles {
    private val posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix")
    private val directoryAttributes = attributes("rwx------")
    private val fileAttributes = attributes("rw-------")

    /** Creates [directory] and its missing parents with `rwx------` where the file system has POSIX permissions. */
    fun directories(directory: Path) {
        Files.createDirectories(directory, *directoryAttributes)
    }

    /** Opens [file] anew with `rw-------`; the mode is set at creation, so the content is never readable by others. */
    fun output(file: Path): OutputStream {
        Files.deleteIfExists(file)
        Files.createFile(file, *fileAttributes)
        return Files.newOutputStream(file)
    }

    private fun attributes(mode: String): Array<FileAttribute<*>> =
        if (posix) arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(mode))) else emptyArray()
}
