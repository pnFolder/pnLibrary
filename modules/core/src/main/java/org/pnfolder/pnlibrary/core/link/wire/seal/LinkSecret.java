package org.pnfolder.pnlibrary.core.link.wire.seal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.common.lang.Failures;

/** The shared link key. The proxy creates the file; game servers only read it, so the link stays off until the admin copies it over. */
public final class LinkSecret {
    public static final String FILE = "link.key";

    private static final int KEY_BYTES = 32;
    private static final String BOM = "﻿";

    private LinkSecret() {
    }

    /** The key in {@code folder}, or null when the file is missing or holds no valid key. */
    public static @Nullable byte[] read(@NotNull Path folder) {
        return Failures.runCatchingNonFatal(() -> {
            String text = new String(Files.readAllBytes(folder.resolve(FILE)), StandardCharsets.UTF_8);
            if (text.startsWith(BOM)) {
                text = text.substring(BOM.length());
            }
            byte[] key = Base64.getDecoder().decode(text.trim());
            return key.length >= KEY_BYTES ? key : null;
        }).getOrNull();
    }

    public static @Nullable byte[] readOrCreate(@NotNull Path folder) {
        return readOrCreate(folder, message -> { });
    }

    // A file that exists but is unreadable is left alone: overwriting it would silently break all servers that have the old key.
    public static @Nullable byte[] readOrCreate(@NotNull Path folder, @NotNull Consumer<String> warn) {
        Path path = folder.resolve(FILE);
        if (Files.exists(path)) {
            byte[] existing = read(folder);
            if (existing == null) {
                warn.accept(FILE + " exists but holds no valid key; the link stays off until it is fixed or deleted");
            }
            return existing;
        }
        byte[] key = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(key);
        return Failures.runCatchingNonFatal(() -> {
            Files.createDirectories(folder);
            create(path, (Base64.getEncoder().encodeToString(key) + '\n').getBytes(StandardCharsets.UTF_8));
            return key;
        }).getOrNull();
    }

    // Owner-only from the first byte, so the key is never readable by others between creation and chmod.
    private static void create(Path path, byte[] content) throws IOException {
        if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Files.write(path, content, StandardOpenOption.WRITE);
        } else {
            Files.write(path, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
    }
}
