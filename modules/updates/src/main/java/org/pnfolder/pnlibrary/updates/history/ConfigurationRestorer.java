package org.pnfolder.pnlibrary.updates.history;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.backup.BackupLabel;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateConfigurationBackup;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;
import org.pnfolder.pnlibrary.common.io.TextFiles;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.updates.engine.UpdatePaths;

/**
 * Offline-only restoration engine. Runtime commands must queue work, never call this on live files.
 * Every restore is journaled, so one interrupted by a crash is rolled back to the state kept before it.
 */
public final class ConfigurationRestorer {
    private static final Set<String> RECOVERABLE = new HashSet<String>(Arrays.asList("APPLYING", "RECOVERING"));
    private static final Set<String> UNDOABLE = new HashSet<String>(Arrays.asList("APPLIED", "APPLYING", "RECOVERING"));
    private static final Set<String> STATES =
        new HashSet<String>(Arrays.asList("PREPARED", "APPLYING", "RECOVERING", "APPLIED", "RECOVERED"));

    private final Gson gson = new Gson();
    private final ConfigurationSnapshots snapshots;
    private final Path directory;

    /** Creates a restorer that keeps its journals under {@code directory}. */
    public ConfigurationRestorer(@NotNull ConfigurationSnapshots snapshots, @NotNull Path directory) {
        this.snapshots = snapshots;
        this.directory = directory;
    }

    /** Preserves current files first, then replaces only paths explicitly recorded in the snapshot. */
    @NotNull
    public UUID restore(@NotNull UUID source) throws IOException {
        return restore(source, null);
    }

    /**
     * Like {@link #restore(UUID)}, but a restore of the same {@code operation} that already finished is not repeated;
     * its id is returned.
     */
    @NotNull
    public UUID restore(@NotNull UUID source, @Nullable UUID operation) throws IOException {
        ConfigurationSnapshot snapshot = snapshots.read(source);
        if (operation != null) {
            UUID finished = finishedRestore(source, operation);
            if (finished != null) {
                return finished;
            }
        }
        UUID before = Objects.requireNonNull(snapshots.capture(UUID.fromString(snapshot.getPlanId()),
            declarations(snapshot)), "Required value was null.");
        UUID id = UUID.randomUUID();
        Path file = UpdatePaths.inside(directory.resolve(id.toString()).resolve("restore.json"), directory);
        ConfigurationRestoreJournal journal = new ConfigurationRestoreJournal(id.toString(), source.toString(),
            before.toString(), "PREPARED", operation == null ? null : operation.toString());
        save(file, journal);
        try {
            journal.setState("APPLYING");
            save(file, journal);
            apply(source);
            journal.setState("APPLIED");
            save(file, journal);
        } catch (Throwable error) {
            Failures.runCatchingNonFatal(() -> {
                recover(file, journal);
                return null;
            }).onFailure(error::addSuppressed);
            throw error;
        }
        return id;
    }

    @Nullable
    private UUID finishedRestore(UUID source, UUID operation) throws IOException {
        List<ConfigurationRestoreJournal> previous = new ArrayList<ConfigurationRestoreJournal>();
        for (Path file : journals()) {
            ConfigurationRestoreJournal journal = readJournal(file);
            if (operation.toString().equals(journal.getOperation())) {
                previous.add(journal);
            }
        }
        for (ConfigurationRestoreJournal journal : previous) {
            if (!journal.getSource().equals(source.toString())) {
                throw new IllegalArgumentException("Restore operation was bound to another snapshot");
            }
        }
        for (ConfigurationRestoreJournal journal : previous) {
            if (journal.getState().equals("APPLIED")) {
                return UUID.fromString(journal.getId());
            }
        }
        return null;
    }

    private static Map<ProductId, UpdateConfigurationBackup> declarations(ConfigurationSnapshot snapshot) {
        Map<String, List<ConfigurationSnapshotFile>> byProduct =
            new LinkedHashMap<String, List<ConfigurationSnapshotFile>>();
        for (ConfigurationSnapshotFile file : snapshot.getFiles()) {
            if (!byProduct.containsKey(file.getProduct())) {
                byProduct.put(file.getProduct(), new ArrayList<ConfigurationSnapshotFile>());
            }
            byProduct.get(file.getProduct()).add(file);
        }
        Map<ProductId, UpdateConfigurationBackup> result = new LinkedHashMap<ProductId, UpdateConfigurationBackup>();
        for (Map.Entry<String, List<ConfigurationSnapshotFile>> entry : byProduct.entrySet()) {
            List<ConfigurationSnapshotFile> files = entry.getValue();
            Set<String> roots = new HashSet<String>();
            List<String> relatives = new ArrayList<String>();
            Map<String, BackupLabel> labels = new LinkedHashMap<String, BackupLabel>();
            for (ConfigurationSnapshotFile file : files) {
                roots.add(file.getRoot());
                relatives.add(file.getRelative());
                labels.put(file.getRelative(),
                    new BackupLabel(file.getBackupKind(), file.getName() == null ? "" : file.getName()));
            }
            if (roots.size() != 1) {
                throw new IllegalArgumentException("Component has conflicting configuration roots");
            }
            result.put(ProductId.of(entry.getKey()),
                new UpdateConfigurationBackup(Paths.get(files.get(0).getRoot()), relatives, labels));
        }
        return result;
    }

    /** Rolls back interrupted restores to the pre-restore snapshot before accepting more work. */
    public void recoverInterrupted() throws IOException {
        for (Path file : journals()) {
            ConfigurationRestoreJournal journal = readJournal(file);
            if (RECOVERABLE.contains(journal.getState())) {
                recover(file, journal);
            }
        }
    }

    /** Reverts a completed configuration restore if its enclosing JAR installation cannot finish. */
    public void undo(@NotNull UUID id) throws IOException {
        Path file = directory.resolve(id.toString()).resolve("restore.json");
        ConfigurationRestoreJournal journal = readJournal(file);
        if (journal.getState().equals("RECOVERED")) {
            return;
        }
        if (!UNDOABLE.contains(journal.getState())) {
            throw new IllegalArgumentException("Configuration restore has not been applied");
        }
        recover(file, journal);
    }

    private List<Path> journals() throws IOException {
        List<Path> result = new ArrayList<Path>();
        if (!Files.exists(directory)) {
            return result;
        }
        try (Stream<Path> folders = Files.list(directory)) {
            for (Path folder : (Iterable<Path>) folders::iterator) {
                if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                Path file = UpdatePaths.inside(folder.resolve("restore.json"), directory);
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    result.add(file);
                }
            }
        }
        return result;
    }

    private ConfigurationRestoreJournal readJournal(Path path) throws IOException {
        Path file = UpdatePaths.inside(path, directory);
        require(Files.size(file) <= 64 * 1024, "Restore journal exceeds size limit");
        String text = TextFiles.read(file, StandardCharsets.UTF_8);
        ConfigurationRestoreJournal journal = gson.fromJson(text, ConfigurationRestoreJournal.class);
        require(UUID.fromString(journal.getId()).toString().equals(file.getParent().getFileName().toString()),
            "Restore identity mismatch");
        UUID.fromString(journal.getSource());
        UUID.fromString(journal.getBefore());
        if (journal.getOperation() != null) {
            UUID.fromString(journal.getOperation());
        }
        require(STATES.contains(journal.getState()), "Invalid restore state");
        return journal;
    }

    private void recover(Path file, ConfigurationRestoreJournal journal) throws IOException {
        journal.setState("RECOVERING");
        save(file, journal);
        apply(UUID.fromString(journal.getBefore()));
        journal.setState("RECOVERED");
        save(file, journal);
    }

    private void apply(UUID id) throws IOException {
        ConfigurationSnapshot snapshot = snapshots.read(id);
        // Check every target before touching the first, so one bad record cannot leave a half-restored set.
        for (ConfigurationSnapshotFile record : snapshot.getFiles()) {
            Path target = snapshots.target(record);
            boolean regular = Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS);
            require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS) || regular,
                "Configuration target is not a regular file");
        }
        for (ConfigurationSnapshotFile record : snapshot.getFiles()) {
            Path target = snapshots.target(record);
            if (record.getExisted()) {
                restoreFile(id, record, target);
            } else {
                Files.deleteIfExists(target);
            }
        }
    }

    private void restoreFile(UUID id, ConfigurationSnapshotFile record, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        FileAttribute<?>[] attributes = new FileAttribute<?>[0];
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            attributes = new FileAttribute<?>[] {
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))};
        }
        Path temporary = Files.createTempFile(target.getParent(), ".pnlibrary-restore-", ".tmp", attributes);
        try {
            copyAcl(target, temporary);
            snapshots.copyForRestore(id, record, temporary);
            // A directory on the path could have been replaced by a link while the blob was being copied.
            snapshots.target(record);
            AtomicFiles.move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Keeps the access rights of the file being replaced; a temporary file gets the directory defaults instead. */
    private static void copyAcl(Path from, Path to) throws IOException {
        if (!Files.exists(from, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        AclFileAttributeView source =
            Files.getFileAttributeView(from, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        AclFileAttributeView destination =
            Files.getFileAttributeView(to, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (source == null || destination == null) {
            return;
        }
        destination.setAcl(source.getAcl());
    }

    private void save(Path file, ConfigurationRestoreJournal journal) throws IOException {
        UpdatePaths.inside(file, directory);
        AtomicFiles.write(file, gson.toJson(journal).getBytes(StandardCharsets.UTF_8), true);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
