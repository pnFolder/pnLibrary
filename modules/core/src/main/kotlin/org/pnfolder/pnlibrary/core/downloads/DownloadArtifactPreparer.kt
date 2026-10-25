package org.pnfolder.pnlibrary.core.downloads

import org.pnfolder.pnlibrary.api.downloads.DownloadDestination
import org.pnfolder.pnlibrary.api.downloads.FileDownload
import org.pnfolder.pnlibrary.api.downloads.FileDownloads
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.common.io.Digests
import org.pnfolder.pnlibrary.common.jvm.JavaRuntime
import org.pnfolder.pnlibrary.core.lifecycle.ResourceCleanup
import org.pnfolder.pnlibrary.core.updates.engine.TrustedHttpClient
import java.nio.file.Files
import java.nio.file.Path
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
        require(declaration.source.supports(platform, JavaRuntime.feature)) {
            "файл не поддерживает ${platform.displayName} / Java ${JavaRuntime.feature}"
        }

        val target = target(request, declaration)
        val limit = declaration.source.size?.coerceAtMost(maximumBytes)?.toInt() ?: maximumBytes.toInt()
        val bytes = http.get(declaration.source.uri, limit)
        verifyIntegrity(declaration, bytes)

        val staging = libraryData.resolve("downloads/staging").resolve("${UUID.randomUUID()}-${target.fileName}")
        Files.createDirectories(staging.parent)
        try {
            Files.write(staging, bytes)
            verifier?.invoke(declaration, staging)
            return PreparedDownload(staging, target)
        } catch (error: Throwable) {
            ResourceCleanup.suppressInto(error, { Files.deleteIfExists(staging) })
            throw error
        }
    }

    private fun verifyIntegrity(declaration: FileDownload, bytes: ByteArray) {
        declaration.source.size?.let { expected ->
            require(bytes.size.toLong() == expected) { "размер файла не совпадает" }
        }
        declaration.source.sha256?.let { expected ->
            require(Digests.sha256(bytes).equals(expected, true)) { "SHA-256 файла не совпадает" }
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

}
