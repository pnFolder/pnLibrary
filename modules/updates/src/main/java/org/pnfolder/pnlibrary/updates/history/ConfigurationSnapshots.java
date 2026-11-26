package org.pnfolder.pnlibrary.updates.history;

import java.util.Locale;
import com.google.gson.Gson;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.backup.BackupKind;
import org.pnfolder.pnlibrary.api.updates.backup.BackupLabel;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateConfigurationBackup;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;
import org.pnfolder.pnlibrary.common.io.TextFiles;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.updates.engine.UpdatePaths;

/** Explicit, bounded configuration snapshots. Capturing never mutates live configuration files. */
public final class ConfigurationSnapshots {
    private static final int MAXIMUM_FILES = 100;

    private final Gson gson = new Gson();
    private final Path directory;
    private final Path pluginsDirectory;
    private final long maximumFileBytes;
    private final long maximumTotalBytes;
    private final long maximumDataFileBytes;
    private final long maximumDataTotalBytes;

    /** Creates snapshots with the default limits: 4 MB per configuration file, 16 MB in all, 1 GB and 4 GB for data. */
    public ConfigurationSnapshots(@NotNull Path directory, @NotNull Path pluginsDirectory) {
        this(directory, pluginsDirectory, 4L * 1024 * 1024, 16L * 1024 * 1024, 1024L * 1024 * 1024,
            4L * 1024 * 1024 * 1024);
    }

    /** Creates snapshots with the given size limits in bytes; all must be positive. */
    public ConfigurationSnapshots(@NotNull Path directory, @NotNull Path pluginsDirectory, long maximumFileBytes,
                                  long maximumTotalBytes, long maximumDataFileBytes, long maximumDataTotalBytes) {
        if (maximumFileBytes <= 0 || maximumTotalBytes <= 0
            || maximumDataFileBytes <= 0 || maximumDataTotalBytes <= 0) {
            throw new IllegalArgumentException("Failed requirement.");
        }
        this.directory = directory;
        this.pluginsDirectory = pluginsDirectory;
        this.maximumFileBytes = maximumFileBytes;
        this.maximumTotalBytes = maximumTotalBytes;
        this.maximumDataFileBytes = maximumDataFileBytes;
        this.maximumDataTotalBytes = maximumDataTotalBytes;
    }

    private long fileLimit(BackupKind kind) {
        return kind == BackupKind.CONFIGURATION ? maximumFileBytes : maximumDataFileBytes;
    }

    /**
     * Saves the selected files of {@code selections} for {@code planId}.
     *
     * @return the snapshot id, or null when nothing is selected
     */
    @Nullable
    public UUID capture(@NotNull UUID planId, @NotNull Map<ProductId, UpdateConfigurationBackup> selections)
        throws IOException {
        if (selections.isEmpty()) {
            return null;
        }
        int count = 0;
        for (UpdateConfigurationBackup selection : selections.values()) {
            count += selection.getFiles().size();
        }
        require(count <= MAXIMUM_FILES, "Configuration snapshot exceeds 100 files");
        List<Source> sources = new ArrayList<>();
        Set<Path> distinct = new HashSet<>();
        for (Map.Entry<ProductId, UpdateConfigurationBackup> entry : selections.entrySet()) {
            for (String relative : entry.getValue().getFiles()) {
                Source source = new Source(entry.getKey(), entry.getValue(), relative,
                    checkedSource(entry.getValue().getRoot(), relative));
                distinct.add(source.path);
                sources.add(source);
            }
        }
        require(distinct.size() == sources.size(), "Configuration files overlap across components");
        UUID id = UUID.randomUUID();
        Path target = directory.toAbsolutePath().normalize().resolve(id.toString());
        UpdatePaths.inside(target.resolve("manifest.json"), directory);
        Files.createDirectories(directory);
        createPrivateDirectory(target);
        try {
            ConfigurationSnapshot snapshot = new ConfigurationSnapshot(1, id.toString(), planId.toString(),
                Instant.now().toString(), copyAll(sources, target));
            writeManifest(target, snapshot);
        } catch (Throwable error) {
            Failures.runCatchingNonFatal(() -> {
                BackupStorage.delete(target);
                return null;
            }).onFailure(error::addSuppressed);
            throw error;
        }
        return id;
    }

    private static final class Source {
        private final ProductId product;
        private final UpdateConfigurationBackup request;
        private final String relative;
        private final Path path;

        Source(ProductId product, UpdateConfigurationBackup request, String relative, Path path) {
            this.product = product;
            this.request = request;
            this.relative = relative;
            this.path = path;
        }
    }

    private static void createPrivateDirectory(Path target) throws IOException {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.createDirectory(target,
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        } else {
            Files.createDirectory(target);
        }
    }

    private List<ConfigurationSnapshotFile> copyAll(List<Source> sources, Path target) throws IOException {
        long configurationTotal = 0;
        long dataTotal = 0;
        List<ConfigurationSnapshotFile> records = new ArrayList<>();
        for (int index = 0; index < sources.size(); index++) {
            Source source = sources.get(index);
            BackupLabel label = source.request.labelOf(source.relative);
            if (!Files.exists(source.path, LinkOption.NOFOLLOW_LINKS)) {
                records.add(new ConfigurationSnapshotFile(source.product.getValue(), source.request.getRoot().toString(),
                    source.relative, false, null, 0, null, label.getKind().name(), label.getName()));
                continue;
            }
            boolean isConfiguration = label.getKind() == BackupKind.CONFIGURATION;
            long room = isConfiguration ? maximumTotalBytes - configurationTotal : maximumDataTotalBytes - dataTotal;
            String blob = String.format(Locale.ROOT, "%04d", index) + ".bin";
            long limit = Math.min(fileLimit(label.getKind()), room);
            ConfigurationSnapshotFile record = copyFile(source, label, blob, target, limit);
            if (isConfiguration) {
                configurationTotal += record.getSize();
            } else {
                dataTotal += record.getSize();
            }
            records.add(record);
        }
        return records;
    }

    /** Copies one existing file and fails when it changed while it was being read. */
    private ConfigurationSnapshotFile copyFile(Source source, BackupLabel label, String blob, Path target, long limit)
        throws IOException {
        BasicFileAttributes before = Files.readAttributes(source.path, BasicFileAttributes.class,
            LinkOption.NOFOLLOW_LINKS);
        require(before.isRegularFile(), "Configuration selection must name a regular file");
        require(before.size() <= limit, "Backup of " + source.relative + " exceeds the size limit");
        SnapshotBlobs.Copied copied = SnapshotBlobs.copyNew(source.path, target.resolve(blob), limit);
        BasicFileAttributes after = Files.readAttributes(source.path, BasicFileAttributes.class,
            LinkOption.NOFOLLOW_LINKS);
        require(before.size() == after.size() && before.lastModifiedTime().equals(after.lastModifiedTime())
            && Objects.equals(before.fileKey(), after.fileKey()), "Configuration changed while being captured");
        return new ConfigurationSnapshotFile(source.product.getValue(), source.request.getRoot().toString(),
            source.relative, true, blob, copied.getSize(), copied.getSha256(), label.getKind().name(), label.getName());
    }

    private void writeManifest(Path target, ConfigurationSnapshot snapshot) throws IOException {
        Path temporary = target.resolve("manifest.tmp");
        TextFiles.write(temporary, gson.toJson(snapshot), StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        AtomicFiles.move(temporary, target.resolve("manifest.json"), false);
    }

    /** Validates the complete manifest and every backup byte before any restoration may begin. */
    @NotNull
    public ConfigurationSnapshot read(@NotNull UUID id) throws IOException {
        Path folder = directory.toAbsolutePath().normalize().resolve(id.toString());
        Path manifest = UpdatePaths.inside(folder.resolve("manifest.json"), directory);
        require(Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS) && Files.size(manifest) <= 1024 * 1024,
            "Invalid configuration manifest");
        String text = TextFiles.read(manifest, StandardCharsets.UTF_8);
        ConfigurationSnapshot snapshot = gson.fromJson(text, ConfigurationSnapshot.class);
        require(snapshot.getSchema() == 1 && snapshot.getId().equals(id.toString()),
            "Configuration snapshot identity mismatch");
        UUID.fromString(snapshot.getPlanId());
        Instant.parse(snapshot.getCreatedAt());
        require(!snapshot.getFiles().isEmpty() && snapshot.getFiles().size() <= MAXIMUM_FILES,
            "Invalid configuration file count");
        Set<Path> sources = new HashSet<>();
        Set<String> blobs = new HashSet<>();
        long configurationTotal = 0;
        long dataTotal = 0;
        for (ConfigurationSnapshotFile file : snapshot.getFiles()) {
            ProductId.of(file.getProduct());
            UpdateConfigurationBackup declaration = new UpdateConfigurationBackup(Paths.get(file.getRoot()),
                Collections.singletonList(file.getRelative()));
            require(sources.add(checkedSource(declaration.getRoot(), declaration.getFiles().get(0))),
                "Duplicate configuration target");
            BackupKind kind = file.getBackupKind();
            require(file.getSize() >= 0 && file.getSize() <= fileLimit(kind), "Invalid backup size");
            if (kind == BackupKind.CONFIGURATION) {
                configurationTotal += file.getSize();
            } else {
                dataTotal += file.getSize();
            }
            require(configurationTotal <= maximumTotalBytes && dataTotal <= maximumDataTotalBytes,
                "Backup snapshot exceeds size limit");
            checkRecord(file, kind, folder, blobs);
        }
        return snapshot;
    }

    private void checkRecord(ConfigurationSnapshotFile file, BackupKind kind, Path folder, Set<String> blobs)
        throws IOException {
        if (!file.getExisted()) {
            require(file.getBlob() == null && file.getSha256() == null && file.getSize() == 0L,
                "Invalid absent configuration record");
            return;
        }
        require(file.getBlob() != null && file.getBlob().matches("[0-9]{4}\\.bin") && blobs.add(file.getBlob()),
            "Invalid configuration blob");
        Path blob = UpdatePaths.inside(folder.resolve(file.getBlob()), folder);
        require(Files.isRegularFile(blob, LinkOption.NOFOLLOW_LINKS) && Files.size(blob) == file.getSize(),
            "Configuration backup size mismatch");
        require(SnapshotBlobs.hash(blob, fileLimit(kind)).equals(file.getSha256()),
            "Configuration backup checksum mismatch");
    }

    private Path checkedSource(Path root, String relative) throws IOException {
        UpdatePaths.inside(root, pluginsDirectory);
        Path source = UpdatePaths.inside(root.resolve(relative), root);
        return UpdatePaths.inside(source, pluginsDirectory);
    }

    /** The live path a {@code file} record would be restored to, checked to stay inside the plugins folder. */
    @NotNull
    public Path target(@NotNull ConfigurationSnapshotFile file) throws IOException {
        return checkedSource(Paths.get(file.getRoot()), file.getRelative());
    }

    /** Copies a validated blob to a temporary file, checking its bytes again during the copy. */
    public void copyForRestore(@NotNull UUID id, @NotNull ConfigurationSnapshotFile file, @NotNull Path temporary)
        throws IOException {
        require(file.getExisted() && file.getBlob() != null, "Configuration record has no backup content");
        Path folder = directory.toAbsolutePath().normalize().resolve(id.toString());
        Path blob = UpdatePaths.inside(folder.resolve(file.getBlob()), folder);
        SnapshotBlobs.Copied copied = SnapshotBlobs.copyOver(blob, temporary, file.getSize());
        require(copied.getSize() == file.getSize() && copied.getSha256().equals(file.getSha256()),
            "Configuration backup changed during restoration");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
