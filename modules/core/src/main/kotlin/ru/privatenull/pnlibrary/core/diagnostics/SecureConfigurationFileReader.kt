package ru.privatenull.pnlibrary.core.diagnostics

import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads a bounded regular file confined to a trusted configuration directory.
 *
 * Validation and reading are intentionally centralized so exact and redacted
 * diagnostics follow the same path policy. Symbolic links, traversal outside the
 * root, non-regular files, known binary formats, and files larger than [maximumBytes]
 * are rejected before their contents enter the report pipeline.
 *
 * @param maximumBytes largest accepted file size in bytes
 */
internal class SecureConfigurationFileReader(
    private val maximumBytes: Long = DEFAULT_MAXIMUM_BYTES,
) {
    /** Result of one confined file read. Exactly one of [content] and [error] is set. */
    data class Result(
        val content: ByteArray? = null,
        val error: String? = null,
    )

    /**
     * Resolves [relativePath] beneath [rootDirectory] and reads its bytes.
     *
     * Operational failures are represented by [Result.error], allowing report
     * generation to continue with an explanatory entry.
     */
    fun read(rootDirectory: Path, relativePath: String): Result {
        val root = rootDirectory.toAbsolutePath().normalize()
        val target = root.resolve(relativePath).normalize()
        if (!target.startsWith(root)) return failure("[SECURITY: path traversal blocked]")
        if (Files.isSymbolicLink(target)) return failure("[SECURITY: symlink escape blocked]")
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            return failure("[file not found or not a regular file]")
        }
        if (hasForbiddenExtension(relativePath)) {
            return failure("[SECURITY: binary or database file extension blocked]")
        }

        val realRoot = resolveRealPath(root)
            ?: return failure("[SECURITY: configuration root cannot be resolved]")
        val realTarget = resolveRealPath(target)
            ?: return failure("[SECURITY: configuration path cannot be resolved]")
        if (!realTarget.startsWith(realRoot)) {
            return failure("[SECURITY: symlink escape blocked]")
        }

        return try {
            val size = Files.size(realTarget)
            if (size > maximumBytes) {
                failure("[file exceeds size limit of $maximumBytes bytes ($size bytes)]")
            } else {
                Result(content = Files.readAllBytes(realTarget))
            }
        } catch (exception: Exception) {
            failure("[read failed: ${exception.javaClass.simpleName}]")
        }
    }

    private fun resolveRealPath(path: Path): Path? = try {
        path.toRealPath()
    } catch (_: Exception) {
        null
    }

    private fun hasForbiddenExtension(path: String): Boolean {
        val lowercase = path.lowercase()
        return FORBIDDEN_EXTENSIONS.any(lowercase::endsWith)
    }

    private fun failure(message: String): Result = Result(error = message)

    private companion object {
        const val DEFAULT_MAXIMUM_BYTES = 1_048_576L
        val FORBIDDEN_EXTENSIONS = setOf(
            ".db", ".sqlite", ".sqlite3", ".db-shm", ".db-wal", ".bin", ".dat",
            ".class", ".jar", ".zip", ".tar", ".gz", ".png", ".jpg", ".jpeg", ".ico",
        )
    }
}
