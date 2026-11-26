package org.pnfolder.pnlibrary.api.updates.backup;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Explicit component-owned files (settings, databases, player data) to preserve before updates.
 * No directory recursion or wildcard selection is performed. Files may contain secrets: the
 * server data directory and its backups must remain private to the server administrator.
 */
public final class UpdateConfigurationBackup {
    private final Path root;
    private final List<String> files;
    private final Map<String, BackupLabel> labels;

    /** Declares plain configuration files with no labels. */
    public UpdateConfigurationBackup(@NotNull Path root, @NotNull List<String> files) {
        this(root, files, Collections.<String, BackupLabel>emptyMap());
    }

    /**
     * Declares files with labels.
     *
     * @param root component data directory, resolved absolutely at declaration time
     * @param files explicit file names relative to {@code root}
     * @param labels kind and name of the files that are not plain configuration, by file name
     */
    public UpdateConfigurationBackup(@NotNull Path root, @NotNull List<String> files,
                                     @NotNull Map<String, BackupLabel> labels) {
        this.root = root.toAbsolutePath().normalize();
        List<String> normalized = new ArrayList<String>();
        for (String value : files) {
            normalized.add(normalizedPath(value));
        }
        this.files = Collections.unmodifiableList(normalized);
        Map<String, BackupLabel> normalizedLabels = new LinkedHashMap<String, BackupLabel>();
        for (Map.Entry<String, BackupLabel> entry : labels.entrySet()) {
            normalizedLabels.put(Paths.get(entry.getKey()).normalize().toString(), entry.getValue());
        }
        this.labels = Collections.unmodifiableMap(normalizedLabels);
        validate();
    }

    private static String normalizedPath(String value) {
        Path path = Paths.get(value).normalize();
        if (value.trim().isEmpty() || path.isAbsolute() || path.getRoot() != null || path.toString().isEmpty()
            || path.toString().equals(".") || path.startsWith("..")) {
            throw new IllegalArgumentException(
                "Configuration paths must stay relative to the component data directory");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '*' || c == '?' || Character.isISOControl(c)) {
                throw new IllegalArgumentException("Configuration paths must be explicit file names");
            }
        }
        return path.toString();
    }

    private void validate() {
        if (!files.containsAll(labels.keySet())) {
            throw new IllegalArgumentException("A backup label names a file that is not declared");
        }
        if (files.isEmpty() || files.size() > 100) {
            throw new IllegalArgumentException("Configuration backup must declare between 1 and 100 files");
        }
        Set<Path> distinct = new HashSet<Path>();
        for (String file : files) {
            distinct.add(Paths.get(file));
        }
        if (distinct.size() != files.size()) {
            throw new IllegalArgumentException("Duplicate configuration paths");
        }
    }

    /** Component data directory, resolved absolutely at declaration time. */
    @NotNull
    public Path getRoot() {
        return root;
    }

    /** Immutable normalized paths relative to {@link #getRoot()}. Missing files are recorded as absent. */
    @NotNull
    public List<String> getFiles() {
        return files;
    }

    /** Kind and name of the files that were labelled, by their normalized path; the others are configuration. */
    @NotNull
    public Map<String, BackupLabel> getLabels() {
        return labels;
    }

    /** The label of {@code file}, or a configuration label when it has none. */
    @NotNull
    public BackupLabel labelOf(@NotNull String file) {
        BackupLabel label = labels.get(file);
        return label != null ? label : new BackupLabel(BackupKind.CONFIGURATION);
    }
}
