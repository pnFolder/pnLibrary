package ru.privatenull.pnlibrary.core.downloads

import ru.privatenull.pnlibrary.api.downloads.DownloadDestination
import ru.privatenull.pnlibrary.api.downloads.FileDownload
import ru.privatenull.pnlibrary.api.downloads.FileDownloads
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.update.TrustedHttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID

/** Downloads, validates and stages one declared file without publishing it. */
internal class DownloadArtifactPreparer(
    private val platform: PlatformType,
    private val libraryData: Path,
    private val configuration: DownloadConfiguration,
    private val http: TrustedHttpClient,
    private val maximumBytes: Long = 512L * 1024L * 1024L,
) {
    fun prepare(
        request: FileDownloads,
        declaration: FileDownload,
        verifier: ((FileDownload, Path) -> Unit)?,
    ): PreparedDownload {
        require(declaration.source.supports(platform, Runtime.version().feature())) {
            "файл не поддерживает ${platform.displayName} / Java ${Runtime.version().feature()}"
        }

        val limit = declaration.source.size?.coerceAtMost(maximumBytes)?.toInt() ?: maximumBytes.toInt()
        val bytes = http.get(declaration.source.uri, limit)
        verifyIntegrity(declaration, bytes)

        val target = target(request, declaration)
        val staging = libraryData.resolve("downloads/staging").resolve("${UUID.randomUUID()}-${target.fileName}")
        Files.createDirectories(staging.parent)
        Files.write(staging, bytes)
        verifier?.invoke(declaration, staging)
        return PreparedDownload(staging, target)
    }

    private fun verifyIntegrity(declaration: FileDownload, bytes: ByteArray) {
        declaration.source.size?.let { expected ->
            require(bytes.size.toLong() == expected) { "размер файла не совпадает" }
        }
        declaration.source.sha256?.let { expected ->
            require(sha256(bytes).equals(expected, true)) { "SHA-256 файла не совпадает" }
        }
    }

    private fun target(request: FileDownloads, declaration: FileDownload): Path {
        require(declaration.destination in configuration.destinations) {
            "назначение ${declaration.destination} запрещено конфигурацией"
        }
        val root = when (declaration.destination) {
            DownloadDestination.DATA_FOLDER -> requireNotNull(request.dataDirectory) {
                "для DATA_FOLDER передайте папку в downloads(dataDirectory, ...)"
            }
            DownloadDestination.CACHE -> libraryData.resolve("downloads/cache")
        }.toAbsolutePath().normalize()
        return root.resolve(declaration.relativePath).normalize().also { target ->
            require(target.startsWith(root)) { "путь загрузки выходит за разрешённую папку" }
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}
