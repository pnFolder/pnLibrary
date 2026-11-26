package org.pnfolder.pnlibrary.core.observability.store;

import java.util.Objects;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.io.Digests;
import org.pnfolder.pnlibrary.common.io.TextFiles;
import org.pnfolder.pnlibrary.common.lang.Failures;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Stores observation attachments on disk with a manifest, refusing executable and unsafe files. */
public final class AttachmentStore {
    private static final Set<String> BLOCKED_EXTENSIONS = new HashSet<>(
            Arrays.asList("jar", "exe", "dll", "so", "bat", "cmd", "sh", "ps1"));
    private static final Type MANIFEST_TYPE = new TypeToken<List<AttachmentManifestEntry>>() { }.getType();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path directory;
    private final Path manifest;
    private final Map<String, StoredAttachment> attachments = new LinkedHashMap<>();

    /** Opens the store in {@code directory}, creating it and reading the manifest of earlier runs. */
    public AttachmentStore(@NotNull Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.manifest = directory.resolve("manifest.json");
        try {
            Files.createDirectories(directory);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        loadManifest();
    }

    /** Copies {@code source} into the store for an observation. */
    @NotNull
    public synchronized StoredAttachment save(@NotNull String observationId, @NotNull Path source) throws IOException {
        validate(source);
        byte[] bytes = Files.readAllBytes(source);
        return save(observationId, source.getFileName().toString(), contentType(source), bytes);
    }

    /** Stores {@code bytes} for an observation under a declared name and content type. */
    @NotNull
    public synchronized StoredAttachment save(@NotNull String observationId, @NotNull String name,
                                              @NotNull String contentType, @NotNull byte[] bytes) throws IOException {
        validateFileName(name);
        String id = UUID.randomUUID().toString();
        Path storedPath = directory.resolve(id + ".bin");
        Files.write(storedPath, bytes);
        StoredAttachment attachment = new StoredAttachment(id, observationId, name, contentType, bytes.length,
                Digests.sha256(bytes), storedPath);
        attachments.put(id, attachment);
        writeManifest();
        return attachment;
    }

    /** The attachments of one observation. */
    @NotNull
    public synchronized List<StoredAttachment> forObservation(@NotNull String observationId) {
        List<StoredAttachment> found = new ArrayList<>();
        for (StoredAttachment attachment : attachments.values()) {
            if (attachment.getObservationId().equals(observationId)) {
                found.add(attachment);
            }
        }
        return found;
    }

    /** Every stored attachment. */
    @NotNull
    public synchronized List<StoredAttachment> all() {
        return new ArrayList<>(attachments.values());
    }

    /** The stored content of {@code attachment}. */
    @NotNull
    public byte[] read(@NotNull StoredAttachment attachment) throws IOException {
        return Files.readAllBytes(attachment.getStoredPath());
    }

    /** Deletes the attachments of every observation that is not in {@code activeObservationIds}. */
    public synchronized void removeOrphans(@NotNull Set<String> activeObservationIds) throws IOException {
        List<String> orphanIds = new ArrayList<>();
        for (StoredAttachment attachment : attachments.values()) {
            if (!activeObservationIds.contains(attachment.getObservationId())) {
                orphanIds.add(attachment.getId());
            }
        }
        for (String id : orphanIds) {
            Files.deleteIfExists(attachments.remove(id).getStoredPath());
        }
        if (!orphanIds.isEmpty()) {
            writeManifest();
        }
    }

    /** Deletes every attachment. */
    public void clear() throws IOException {
        removeOrphans(Collections.emptySet());
    }

    private void validate(Path source) {
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("Attachment is not a readable file: " + source);
        }
        validateFileName(source.getFileName().toString());
    }

    private static void validateFileName(String name) {
        if (BLOCKED_EXTENSIONS.contains(extensionOf(name))) {
            throw new IllegalArgumentException("Executable attachments are not allowed: " + name);
        }
    }

    private void loadManifest() {
        if (!Files.isRegularFile(manifest)) {
            return;
        }
        List<AttachmentManifestEntry> entries = Failures.runCatchingNonFatal(
                () -> gson.<List<AttachmentManifestEntry>>fromJson(TextFiles.read(manifest), MANIFEST_TYPE))
                .getOrDefault(null);
        if (entries == null) {
            return;
        }
        for (AttachmentManifestEntry entry : entries) {
            Path storedPath = directory.resolve(entry.id + ".bin");
            if (Files.isRegularFile(storedPath)) {
                attachments.put(entry.id, entry.restore(storedPath));
            }
        }
    }

    private void writeManifest() throws IOException {
        List<AttachmentManifestEntry> entries = new ArrayList<>();
        for (StoredAttachment attachment : attachments.values()) {
            entries.add(new AttachmentManifestEntry(attachment));
        }
        TextFiles.write(manifest, gson.toJson(entries));
    }

    private static String contentType(Path path) throws IOException {
        String probed = Files.probeContentType(path);
        if (probed != null) {
            return probed;
        }
        switch (extensionOf(path.getFileName().toString())) {
            case "log":
            case "txt":
                return "text/plain";
            case "json":
                return "application/json";
            case "yml":
            case "yaml":
                return "application/yaml";
            case "xml":
                return "application/xml";
            case "zip":
                return "application/zip";
            default:
                return "application/octet-stream";
        }
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
