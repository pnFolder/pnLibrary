package org.pnfolder.pnlibrary.core.downloads.fetch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;

class AtomicDownloadPublisherTest {
    @TempDir
    Path directory;

    private Path file(String name, String content) throws IOException {
        return Files.write(directory.resolve(name), content.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void existingDirectoryIsPreservedWhenADownloadTargetsIt() throws IOException {
        Path target = directory.resolve("models");
        Files.createDirectories(target);
        Path original = target.resolve("existing.bin");
        Files.write(original, bytes("original"));
        Path staging = file("staging.bin", "download");

        assertThrows(IllegalArgumentException.class, () -> new AtomicDownloadPublisher(directory)
            .publish(Arrays.asList(new PreparedDownload(staging, target))));
        assertArrayEquals(bytes("original"), Files.readAllBytes(original));
        assertFalse(Files.exists(staging));
    }

    @Test
    void publicationFailureRestoresOriginalsAndRemovesNewlyInstalledFiles() throws IOException {
        Path existing = file("existing.bin", "original");
        Path newTarget = directory.resolve("new.bin");
        List<PreparedDownload> downloads = Arrays.asList(
            new PreparedDownload(file("first.tmp", "replacement"), existing),
            new PreparedDownload(file("second.tmp", "new"), newTarget),
            new PreparedDownload(directory.resolve("missing.tmp"), directory.resolve("last.bin")));

        assertThrows(IOException.class, () -> new AtomicDownloadPublisher(directory).publish(downloads));
        assertArrayEquals(bytes("original"), Files.readAllBytes(existing));
        assertFalse(Files.exists(newTarget));
        for (PreparedDownload download : downloads) {
            assertFalse(Files.exists(download.getStaging()));
        }
    }

    @Test
    void failedRestorationPreservesBackupsAndStillRestoresOtherFiles() throws IOException {
        Path first = file("first.bin", "original first");
        Path second = file("second.bin", "original second");
        IOException publicationFailure = new IOException("Publication failed");
        IOException restorationFailure = new IOException("Restoration failed");
        AtomicDownloadPublisher publisher = new AtomicDownloadPublisher(directory, (source, target) -> {
            if (source.getFileName().toString().equals("missing.tmp")) {
                throw publicationFailure;
            }
            if (source.getParent().getParent().getFileName().toString().equals("transactions") && target.equals(second)) {
                throw restorationFailure;
            }
            AtomicFiles.move(source, target);
        });

        IOException thrown = assertThrows(IOException.class, () -> publisher.publish(Arrays.asList(
            new PreparedDownload(file("first.tmp", "new first"), first),
            new PreparedDownload(file("second.tmp", "new second"), second),
            new PreparedDownload(directory.resolve("missing.tmp"), directory.resolve("last.bin")))));
        assertSame(publicationFailure, thrown);
        assertTrue(Arrays.asList(publicationFailure.getSuppressed()).contains(restorationFailure));
        assertArrayEquals(bytes("original first"), Files.readAllBytes(first));
        List<Path> backups = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(directory.resolve("downloads/transactions"))) {
            paths.filter(Files::isRegularFile).forEach(backups::add);
        }
        boolean kept = false;
        for (Path backup : backups) {
            kept |= new String(Files.readAllBytes(backup), StandardCharsets.UTF_8).equals("original second");
        }
        assertTrue(kept);
    }
}
