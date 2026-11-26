package org.pnfolder.pnlibrary.common.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 digests as lowercase hexadecimal, the form used in release catalogs and integrity records. */
public final class Digests {
    private static final String SHA_256 = "SHA-256";
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Digests() {
    }

    /** The SHA-256 of {@code bytes}. */
    @NotNull
    public static String sha256(@NotNull byte[] bytes) {
        return hex(newDigest().digest(bytes));
    }

    /** The SHA-256 of the file at {@code path}, read in chunks so large files are never loaded at once. */
    @NotNull
    public static String sha256(@NotNull Path path) throws IOException {
        MessageDigest digest = newDigest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            for (int read = input.read(buffer); read >= 0; read = input.read(buffer)) {
                digest.update(buffer, 0, read);
            }
        }
        return hex(digest.digest());
    }

    /** {@code bytes} as lowercase hexadecimal, two characters per byte. */
    @NotNull
    public static String hex(@NotNull byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xFF;
            result[index * 2] = HEX[value >>> 4];
            result[index * 2 + 1] = HEX[value & 0x0F];
        }
        return new String(result);
    }

    // Every JVM is required to provide SHA-256, so a missing algorithm is a broken runtime, not a caller error.
    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(SHA_256 + " is not available", error);
        }
    }
}
