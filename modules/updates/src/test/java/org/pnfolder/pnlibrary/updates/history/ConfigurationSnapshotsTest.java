package org.pnfolder.pnlibrary.updates.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateConfigurationBackup;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;

class ConfigurationSnapshotsTest {
    @TempDir
    Path directory;

    @Test
    void snapshotKeepsExactBytesAndAbsenceWithoutChangingTheLiveFiles() throws IOException {
        Path data = data();
        ConfigurationSnapshots store = store();
        Path file = data.resolve("config.yml");
        write(file, "message: Привет\n");
        UUID plan = UUID.randomUUID();
        UUID id = store.capture(plan, selection(data, "config.yml", "missing.yml"));
        write(file, "new setting");
        ConfigurationSnapshot snapshot = store.read(id);
        assertEquals(plan.toString(), snapshot.getPlanId());
        assertTrue(snapshot.getFiles().get(0).getExisted());
        assertFalse(snapshot.getFiles().get(1).getExisted());
        assertEquals("new setting", read(file));
        assertEquals("message: Привет\n",
            read(directory.resolve("snapshots/" + id + "/" + snapshot.getFiles().get(0).getBlob())));
        assertFalse(Files.exists(data.resolve("missing.yml")));
    }

    @Test
    void corruptBackupBytesAndEscapedManifestPathsAreRejected() throws IOException {
        Path data = data();
        ConfigurationSnapshots store = store();
        write(data.resolve("config.yml"), "original");
        UUID id = store.capture(UUID.randomUUID(), selection(data, "config.yml"));
        ConfigurationSnapshot snapshot = store.read(id);
        ConfigurationSnapshotFile original = snapshot.getFiles().get(0);
        Path blob = directory.resolve("snapshots/" + id + "/" + original.getBlob());
        write(blob, "tampered");
        assertThrows(IllegalArgumentException.class, () -> store.read(id));
        write(blob, "original");
        ConfigurationSnapshotFile escaped = new ConfigurationSnapshotFile(original.getProduct(), original.getRoot(),
            "../../outside.yml", original.getExisted(), original.getBlob(), original.getSize(), original.getSha256(),
            original.getKind(), original.getName());
        ConfigurationSnapshot changed = new ConfigurationSnapshot(snapshot.getSchema(), snapshot.getId(),
            snapshot.getPlanId(), snapshot.getCreatedAt(), Collections.singletonList(escaped));
        write(directory.resolve("snapshots/" + id + "/manifest.json"), new Gson().toJson(changed));
        assertThrows(IllegalArgumentException.class, () -> store.read(id));
        assertEquals("original", read(data.resolve("config.yml")));
    }

    @Test
    void directoriesOversizedFilesAndFilesOutsidePluginsAreNotCaptured() throws IOException {
        Path data = data();
        ConfigurationSnapshots store = new ConfigurationSnapshots(directory.resolve("snapshots"),
            directory.resolve("plugins"), 2, 16L * 1024 * 1024, 1024L * 1024 * 1024, 4L * 1024 * 1024 * 1024);
        write(data.resolve("large.yml"), "123");
        assertThrows(IllegalArgumentException.class,
            () -> store.capture(UUID.randomUUID(), selection(data, "large.yml")));
        Files.createDirectory(data.resolve("nested"));
        assertThrows(IllegalArgumentException.class,
            () -> store.capture(UUID.randomUUID(), selection(data, "nested")));
        assertThrows(IllegalArgumentException.class,
            () -> store.capture(UUID.randomUUID(), selection(directory.resolve("outside"), "config.yml")));
        assertThrows(IllegalArgumentException.class,
            () -> new UpdateConfigurationBackup(data, Collections.singletonList("../outside.yml")));
    }

    @Test
    void aFailedCaptureLeavesNoHalfWrittenSnapshotFolder() throws IOException {
        Path data = data();
        ConfigurationSnapshots store = new ConfigurationSnapshots(directory.resolve("snapshots"),
            directory.resolve("plugins"), 4L * 1024 * 1024, 4, 1024L * 1024 * 1024, 4L * 1024 * 1024 * 1024);
        write(data.resolve("a.yml"), "123");
        write(data.resolve("b.yml"), "456");
        Map<ProductId, UpdateConfigurationBackup> selection = selection(data, "a.yml", "b.yml");
        assertThrows(IllegalArgumentException.class, () -> store.capture(UUID.randomUUID(), selection));
        try (Stream<Path> files = Files.list(directory.resolve("snapshots"))) {
            assertEquals(0, files.count());
        }
    }

    private Path data() throws IOException {
        return Files.createDirectories(directory.resolve("plugins").resolve("example"));
    }

    private ConfigurationSnapshots store() {
        return new ConfigurationSnapshots(directory.resolve("snapshots"), directory.resolve("plugins"));
    }

    private static Map<ProductId, UpdateConfigurationBackup> selection(Path data, String... files) {
        return Collections.singletonMap(ProductId.of("example"),
            new UpdateConfigurationBackup(data, Arrays.asList(files)));
    }

    private static void write(Path file, String text) throws IOException {
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
