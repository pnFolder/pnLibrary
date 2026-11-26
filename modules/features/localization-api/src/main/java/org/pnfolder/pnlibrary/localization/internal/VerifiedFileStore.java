package org.pnfolder.pnlibrary.localization.internal;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Cache directory layout for localization files with bounded reads, atomic writes, SHA-1 helpers and quarantine for
 * files that fail verification.
 */
final class VerifiedFileStore {
    private static final int DEFAULT_READ_LIMIT = 64 * 1024 * 1024;

    private final Path root;

    VerifiedFileStore(Path root) {
        this.root = root;
    }

    /** Cache file of one language of one version. */
    Path translation(String version, String locale) {
        return root.resolve("translations").resolve(version).resolve(locale + ".json");
    }

    /** Cache file of the Mojang version manifest. */
    Path manifest() {
        return root.resolve("version-manifest.json");
    }

    /** Cache file of the metadata of {@code version}. */
    Path versionMetadata(String version) {
        return root.resolve("versions").resolve(version + ".json");
    }

    /** Cache file of the asset index of {@code version}. */
    Path assetIndex(String version) {
        return root.resolve("asset-indexes").resolve(version + ".json");
    }

    /** Creates an empty temporary file in the cache for a large download. */
    Path temporary(String suffix) throws IOException {
        Path directory = root.resolve("temporary");
        Files.createDirectories(directory);
        return Files.createTempFile(directory, "download-", suffix);
    }

    /** Returns the file bytes, or null when it is missing or larger than the default limit. */
    @Nullable
    byte[] read(Path path) throws IOException {
        return read(path, DEFAULT_READ_LIMIT);
    }

    /** Returns the file bytes, or null when it is missing or larger than {@code maximumBytes}. */
    @Nullable
    byte[] read(Path path, int maximumBytes) throws IOException {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        if (maximumBytes < 0) {
            throw new IllegalArgumentException("Maximum bytes must not be negative");
        }
        if (Files.size(path) > (long) maximumBytes) {
            return null;
        }
        return Files.readAllBytes(path);
    }

    /** Writes {@code bytes} through a temporary file and an atomic move, so readers never see a partial file. */
    void write(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        try {
            Files.write(temporary, bytes);
            AtomicFiles.move(temporary, path);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Renames a file that failed verification so it is neither reused nor lost. */
    void quarantine(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.move(path, path.resolveSibling(path.getFileName() + ".corrupt-" + System.currentTimeMillis()),
                StandardCopyOption.REPLACE_EXISTING);
    }

    /** Lowercase hex SHA-1 of {@code bytes}. */
    String sha1(byte[] bytes) {
        return hex(digest().digest(bytes));
    }

    /** Lowercase hex SHA-1 of the file, streamed so large archives are not loaded into memory. */
    String sha1(Path path) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return hex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static String hex(byte[] digest) {
        StringBuilder text = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            text.append(String.format(Locale.ROOT, "%02x", b));
        }
        return text.toString();
    }
}
