package ru.privatenull.pnlibrary.core.upload

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.time.Clock
import java.util.EnumSet

/** Serializable remote-deletion instruction retained by [UploadLedger]. */
internal data class UploadLedgerEntry(
    val backend: String = "",
    val link: String = "",
    val id: String = "",
    val token: String = "",
    val deleteAt: Long = 0L,
) {
    /** Whether all fields required for a safe deletion attempt are present. */
    fun isValid(): Boolean {
        if (backend.isBlank() || id.isBlank() || token.isBlank() || deleteAt <= 0) return false
        val uri = try {
            URI.create(link)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }
}

/**
 * Atomic JSON persistence for remote-deletion ledger entries.
 *
 * The file is bounded to 1 MiB and may not be a symbolic link. Malformed JSON is
 * quarantined beside the ledger before an empty state is returned, preserving the
 * original bytes for operator recovery instead of silently overwriting tokens.
 */
internal class UploadLedgerPersistence(
    private val file: Path,
    private val clock: Clock,
) {
    /** Reads all structurally valid entries, quarantining malformed JSON. */
    @Throws(IOException::class)
    fun read(): MutableList<UploadLedgerEntry> {
        if (!Files.exists(file)) return mutableListOf()
        if (Files.isSymbolicLink(file)) throw IOException("unsafe upload ledger: symlink")
        if (!Files.isRegularFile(file)) throw IOException("unsafe upload ledger: not a regular file")
        if (Files.size(file) > MAX_LEDGER_BYTES) throw IOException("unsafe upload ledger: oversized")
        return try {
            Files.readString(file, StandardCharsets.UTF_8)
                .let { JSON.fromJson<List<UploadLedgerEntry>?>(it, ENTRY_LIST_TYPE) }
                .orEmpty()
                .filter(UploadLedgerEntry::isValid)
                .toMutableList()
        } catch (_: JsonParseException) {
            quarantineCorruptFile()
            mutableListOf()
        }
    }

    /** Atomically replaces the ledger and applies owner-only POSIX permissions. */
    @Throws(IOException::class)
    fun write(entries: List<UploadLedgerEntry>) {
        val parent = file.toAbsolutePath().parent
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, "${file.fileName}.", ".tmp")
        try {
            Files.writeString(
                temporary,
                JSON.toJson(entries),
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING,
            )
            move(temporary, file)
            applyOwnerOnlyPermissions()
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun quarantineCorruptFile() {
        val quarantine = file.resolveSibling(
            "${file.fileName}.corrupt-${clock.instant().epochSecond}",
        )
        try {
            Files.move(file, quarantine, StandardCopyOption.REPLACE_EXISTING)
        } catch (exception: IOException) {
            throw IOException("corrupt upload ledger could not be quarantined", exception)
        }
    }

    private fun move(source: Path, target: Path) {
        try {
            Files.move(
                source,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun applyOwnerOnlyPermissions() {
        try {
            Files.setPosixFilePermissions(
                file,
                EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        } catch (_: UnsupportedOperationException) {
            // Windows and other non-POSIX filesystems do not expose these permissions.
        } catch (_: IOException) {
            // Permission hardening is best-effort after an otherwise successful write.
        }
    }

    private companion object {
        const val MAX_LEDGER_BYTES = 1_048_576L
        val JSON = Gson()
        val ENTRY_LIST_TYPE = object : TypeToken<List<UploadLedgerEntry>>() {}.type
    }
}
