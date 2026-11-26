package org.pnfolder.pnlibrary.config.yaml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;

/**
 * Owns durable file operations for one YAML configuration document.
 *
 * <p>A replacement is validated while it is still a temporary file. The original
 * file therefore remains intact when encoding or decoding the new document fails.
 */
final class ConfigurationFileStore {
    private static final int MAX_BACKUPS = 5;

    private final File file;
    private final Consumer<String> validator;

    /**
     * Creates the store of one file.
     *
     * @param file the configuration file
     * @param validator accepts a document or throws; run on the temporary copy before it replaces the file
     */
    ConfigurationFileStore(@NotNull File file, @NotNull Consumer<String> validator) {
        this.file = file;
        this.validator = validator;
    }

    /** Converts line endings to a single newline and ends the text with exactly one newline. */
    @NotNull
    String normalize(@NotNull String content) {
        return YamlText.trimEnd(content.replace("\r\n", "\n")) + "\n";
    }

    /** Replaces the file with {@code content} atomically, after the validator accepted it as a temporary file. */
    void write(@NotNull String content) throws IOException {
        replace(content, validator);
    }

    /** Like {@link #write} for a document the caller has just decoded and validated, so it is not decoded a second time. */
    void writeDecoded(@NotNull String content) throws IOException {
        replace(content, ignored -> { });
    }

    private void replace(String content, Consumer<String> check) throws IOException {
        String normalized = normalize(content);
        Path temporary = Files.createTempFile(file.getAbsoluteFile().getParentFile().toPath(), file.getName() + ".", ".tmp");
        try {
            Files.write(temporary, normalized.getBytes(StandardCharsets.UTF_8));
            check.accept(normalized);
            AtomicFiles.move(temporary, file.toPath());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Saves the previous document beside the file, keeping the newest {@value #MAX_BACKUPS} copies. */
    @NotNull
    File backup(@NotNull String content) throws IOException {
        File target = Files.createTempFile(file.getAbsoluteFile().getParentFile().toPath(),
                file.getName() + ".before-sync-", ".bak").toFile();
        try {
            Files.write(target.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable error) {
            // An empty backup would count against the retention limit and push out a real one.
            deleteBestEffort(target);
            throw error;
        }
        // The new backup is kept explicitly: modification times have coarse resolution on some file systems,
        // and a tie must not make the backup just written the one that gets deleted.
        List<File> old = new ArrayList<File>();
        for (File backup : existingBackups()) {
            if (!backup.equals(target)) {
                old.add(backup);
            }
        }
        Collections.sort(old, Comparator.comparingLong(File::lastModified).reversed());
        for (int index = MAX_BACKUPS - 1; index < old.size(); index++) {
            deleteBestEffort(old.get(index));
        }
        return target;
    }

    private List<File> existingBackups() {
        File[] found = file.getAbsoluteFile().getParentFile().listFiles(candidate ->
                candidate.getName().startsWith(file.getName() + ".before-sync-") && candidate.getName().endsWith(".bak"));
        List<File> backups = new ArrayList<File>();
        if (found != null) {
            Collections.addAll(backups, found);
        }
        return backups;
    }

    private static void deleteBestEffort(File backup) {
        try {
            backup.delete();
        } catch (SecurityException error) {
            // Retention cleanup must not turn a successful configuration load into a failure.
        }
    }
}
