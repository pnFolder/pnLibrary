package ru.privatenull.pnlibrary.core.downloads

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.downloads.DownloadDestination
import ru.privatenull.pnlibrary.api.downloads.FileDownloads
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.update.TrustedHttpClient
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class DownloadArtifactPreparerTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `preparer stages bytes and resolves target inside declared directory`() {
        val payload = "downloaded".toByteArray()
        val request = FileDownloads.builder()
            .dataDirectory(directory.resolve("plugin-data"))
            .file("model") { file ->
                file.url("https://example.org/model.bin")
                    .destination(DownloadDestination.DATA_FOLDER, "models/model.bin")
            }
            .build()
        val http = object : TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1)) {
            override fun get(uri: URI, maximumBytes: Int) = payload
        }
        val preparer = DownloadArtifactPreparer(
            platform = PlatformType.BUKKIT,
            libraryData = directory.resolve("plugins/pnLibrary"),
            configuration = DownloadConfiguration(),
            http = http,
        )

        val prepared = preparer.prepare(request, request.files.single(), verifier = null)

        assertEquals(directory.resolve("plugin-data/models/model.bin").toAbsolutePath(), prepared.target)
        assertArrayEquals(payload, Files.readAllBytes(prepared.staging))
    }
}
