package org.pnfolder.pnlibrary.updates.history.configuration;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateConfigurationBackup;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;

class ConfigurationRestorerTest {
    @TempDir
    Path directory;

    @Test
    void restorationPreservesCurrentFilesFirstAndOnlyChangesDeclaredPaths() throws IOException {
        Path data = data();
        ConfigurationSnapshots snapshots = snapshots();
        Path config = data.resolve("config.yml");
        write(config, "version: 1");
        UUID original = snapshots.capture(UUID.randomUUID(), selection(data, "config.yml", "optional.yml"));
        write(config, "version: 2");
        write(data.resolve("optional.yml"), "new generated file");
        write(data.resolve("unrelated.yml"), "keep me");
        ConfigurationRestorer restorer = new ConfigurationRestorer(snapshots, directory.resolve("restores"));
        UUID result = restorer.restore(original);
        assertEquals("version: 1", read(config));
        assertFalse(Files.exists(data.resolve("optional.yml")));
        assertEquals("keep me", read(data.resolve("unrelated.yml")));
        ConfigurationRestoreJournal journal = journal(result);
        assertEquals("APPLIED", journal.getState());
        ConfigurationSnapshot before = snapshots.read(UUID.fromString(journal.getBefore()));
        for (ConfigurationSnapshotFile file : before.getFiles()) {
            assertTrue(file.getExisted());
        }
        restorer.restore(UUID.fromString(journal.getBefore()));
        assertEquals("version: 2", read(config));
        assertEquals("new generated file", read(data.resolve("optional.yml")));
    }

    @Test
    void interruptedRestoreRecoversTheSavedPreRestoreState() throws IOException {
        Path data = data();
        ConfigurationSnapshots snapshots = snapshots();
        write(data.resolve("config.yml"), "old");
        UUID original = snapshots.capture(UUID.randomUUID(), selection(data, "config.yml"));
        write(data.resolve("config.yml"), "current");
        Path root = directory.resolve("restores");
        ConfigurationRestorer restorer = new ConfigurationRestorer(snapshots, root);
        UUID result = restorer.restore(original);
        ConfigurationRestoreJournal saved = journal(result);
        ConfigurationRestoreJournal interrupted = new ConfigurationRestoreJournal(saved.getId(), saved.getSource(),
            saved.getBefore(), "APPLYING", saved.getOperation());
        write(root.resolve(result + "/restore.json"), new Gson().toJson(interrupted));
        new ConfigurationRestorer(snapshots, root).recoverInterrupted();
        assertEquals("current", read(data.resolve("config.yml")));
        assertEquals("RECOVERED", journal(result).getState());
    }

    @Test
    void damagedSourceSnapshotCannotModifyAnyLiveFile() throws IOException {
        Path data = data();
        ConfigurationSnapshots snapshots = snapshots();
        write(data.resolve("config.yml"), "old");
        UUID original = snapshots.capture(UUID.randomUUID(), selection(data, "config.yml"));
        write(data.resolve("config.yml"), "current");
        ConfigurationSnapshotFile record = snapshots.read(original).getFiles().get(0);
        write(directory.resolve("snapshots/" + original + "/" + record.getBlob()), "bad");
        assertThrows(IllegalArgumentException.class,
            () -> new ConfigurationRestorer(snapshots, directory.resolve("restores")).restore(original));
        assertEquals("current", read(data.resolve("config.yml")));
    }

    @Test
    void completedOperationIsNotRepeatedAndCanBeUndoneByItsEnclosingTransaction() throws IOException {
        Path data = data();
        ConfigurationSnapshots snapshots = snapshots();
        write(data.resolve("config.yml"), "old");
        UUID original = snapshots.capture(UUID.randomUUID(), selection(data, "config.yml"));
        write(data.resolve("config.yml"), "current");
        ConfigurationRestorer restorer = new ConfigurationRestorer(snapshots, directory.resolve("restores"));
        UUID operation = UUID.randomUUID();
        UUID restored = restorer.restore(original, operation);
        assertEquals(restored, restorer.restore(original, operation));
        assertEquals("old", read(data.resolve("config.yml")));
        restorer.undo(restored);
        assertEquals("current", read(data.resolve("config.yml")));
        assertEquals("RECOVERED", journal(restored).getState());
    }

    private ConfigurationRestoreJournal journal(UUID id) throws IOException {
        return new Gson().fromJson(read(directory.resolve("restores/" + id + "/restore.json")),
            ConfigurationRestoreJournal.class);
    }

    private Path data() throws IOException {
        return Files.createDirectories(directory.resolve("plugins").resolve("example"));
    }

    private ConfigurationSnapshots snapshots() {
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
