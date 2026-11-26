package org.pnfolder.pnlibrary.core.downloads.fetch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.downloads.FileDownloads;
import org.pnfolder.pnlibrary.api.downloads.source.DownloadDestination;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.core.downloads.DownloadConfiguration;
import org.pnfolder.pnlibrary.updates.engine.net.TrustedHttpClient;

class DownloadArtifactPreparerTest {
    @TempDir
    Path directory;

    private static TrustedHttpClient answering(byte[] payload) {
        return new TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1)) {
            @Override
            public byte[] get(URI uri, int maximumBytes) {
                return payload;
            }
        };
    }

    @Test
    void preparerStagesBytesAndResolvesTargetInsideDeclaredDirectory() throws Exception {
        byte[] payload = "downloaded".getBytes(StandardCharsets.UTF_8);
        FileDownloads request = FileDownloads.builder()
            .dataDirectory(directory.resolve("plugin-data"))
            .file("model", file -> file.url("https://example.org/model.bin")
                .destination(DownloadDestination.DATA_FOLDER, "models/model.bin"))
            .build();
        DownloadArtifactPreparer preparer = new DownloadArtifactPreparer(PlatformType.BUKKIT,
            directory.resolve("plugins/pnLibrary"), DownloadConfiguration::new, answering(payload));

        PreparedDownload prepared = preparer.prepare(request, request.getFiles().get(0), null);

        assertEquals(directory.resolve("plugin-data/models/model.bin").toAbsolutePath(), prepared.getTarget());
        assertArrayEquals(payload, Files.readAllBytes(prepared.getStaging()));
    }

    @Test
    void failedJarVerificationRemovesTheStagedFileAndPreservesTheFailure() throws IOException {
        Path libraryData = directory.resolve("plugins/pnLibrary");
        FileDownloads request = FileDownloads.builder().file("plugin", file -> file
            .url("https://example.org/plugin.jar").destination(DownloadDestination.CACHE, "plugin.jar")).build();
        DownloadArtifactPreparer preparer = new DownloadArtifactPreparer(PlatformType.BUKKIT, libraryData,
            DownloadConfiguration::new, answering("invalid jar".getBytes(StandardCharsets.UTF_8)));
        IllegalArgumentException failure = new IllegalArgumentException("Plugin identity does not match");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
            preparer.prepare(request, request.getFiles().get(0), (declaration, staged) -> {
                throw failure;
            }));
        assertSame(failure, thrown);
        try (Stream<Path> staged = Files.list(libraryData.resolve("downloads/staging"))) {
            assertTrue(!staged.findAny().isPresent());
        }
    }
}
