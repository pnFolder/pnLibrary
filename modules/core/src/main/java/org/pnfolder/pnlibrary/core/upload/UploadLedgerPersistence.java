package org.pnfolder.pnlibrary.core.upload;

import java.util.Objects;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;
import org.pnfolder.pnlibrary.common.io.TextFiles;

/**
 * Atomic JSON persistence for remote-deletion ledger entries.
 *
 * <p>The file is bounded to 1 MiB and may not be a symbolic link. Malformed JSON is quarantined beside the ledger
 * before an empty state is returned, preserving the original bytes for operator recovery instead of silently
 * overwriting tokens.
 */
public final class UploadLedgerPersistence {
    private static final long MAX_LEDGER_BYTES = 1_048_576L;
    private static final Gson JSON = new Gson();
    private static final Type ENTRY_LIST_TYPE = new TypeToken<List<UploadLedgerEntry>>() { }.getType();

    private final Path file;
    private final Clock clock;

    public UploadLedgerPersistence(@NotNull Path file, @NotNull Clock clock) {
        this.file = Objects.requireNonNull(file, "file");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Reads all structurally valid entries, quarantining malformed JSON. */
    public @NotNull List<UploadLedgerEntry> read() throws IOException {
        List<UploadLedgerEntry> valid = new ArrayList<UploadLedgerEntry>();
        if (!Files.exists(file)) {
            return valid;
        }
        if (Files.isSymbolicLink(file)) {
            throw new IOException("Unsafe upload ledger: symlink");
        }
        if (!Files.isRegularFile(file)) {
            throw new IOException("Unsafe upload ledger: not a regular file");
        }
        if (Files.size(file) > MAX_LEDGER_BYTES) {
            throw new IOException("Unsafe upload ledger: oversized");
        }
        try {
            List<UploadLedgerEntry> stored = JSON.fromJson(TextFiles.read(file, StandardCharsets.UTF_8), ENTRY_LIST_TYPE);
            for (UploadLedgerEntry entry : stored == null ? new ArrayList<UploadLedgerEntry>() : stored) {
                if (entry.isValid()) {
                    valid.add(entry);
                }
            }
            return valid;
        } catch (JsonParseException malformed) {
            quarantineCorruptFile();
            return new ArrayList<UploadLedgerEntry>();
        }
    }

    /** Atomically replaces the ledger and applies owner-only POSIX permissions. */
    public void write(@NotNull List<UploadLedgerEntry> entries) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, file.getFileName() + ".", ".tmp");
        try {
            TextFiles.write(temporary, JSON.toJson(entries), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            AtomicFiles.move(temporary, file);
            applyOwnerOnlyPermissions();
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void quarantineCorruptFile() throws IOException {
        Path quarantine = file.resolveSibling(file.getFileName() + ".corrupt-" + clock.instant().getEpochSecond());
        try {
            Files.move(file, quarantine, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IOException("Corrupt upload ledger could not be quarantined", exception);
        }
    }

    private void applyOwnerOnlyPermissions() {
        try {
            Files.setPosixFilePermissions(file, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException nonPosix) {
            // Windows and other non-POSIX filesystems do not expose these permissions.
        } catch (IOException bestEffort) {
            // Permission hardening is best-effort after an otherwise successful write.
        }
    }
}
