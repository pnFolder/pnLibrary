package org.pnfolder.pnlibrary.updates.engine.transaction.journal;

import org.pnfolder.pnlibrary.updates.engine.transaction.TransactionState;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;
import org.pnfolder.pnlibrary.common.io.Digests;
import org.pnfolder.pnlibrary.updates.engine.UpdatePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Confined, validated access to the transaction journals stored below the root. */
public final class TransactionJournalRepository {
    private static final int MAX_JOURNAL_BYTES = 1024 * 1024;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final Path root;
    private final Path publicationRoot;
    private final Consumer<String> warn;

    public TransactionJournalRepository(@NotNull Path root, @NotNull Path publicationRoot) {
        this(root, publicationRoot, message -> {
        });
    }

    public TransactionJournalRepository(@NotNull Path root, @NotNull Path publicationRoot, @NotNull Consumer<String> warn) {
        this.root = Objects.requireNonNull(root, "root");
        this.publicationRoot = Objects.requireNonNull(publicationRoot, "publicationRoot");
        this.warn = Objects.requireNonNull(warn, "warn");
    }

    /** Lists every {@code journal.json} found in a direct child directory of the root. */
    @NotNull
    public List<Path> journals() throws IOException {
        List<Path> result = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return result;
        }
        try (Stream<Path> directories = Files.list(root)) {
            directories.filter(Files::isDirectory).map(directory -> directory.resolve("journal.json"))
                .filter(Files::isRegularFile).forEach(result::add);
        }
        return result;
    }

    /** Loads every readable journal; an empty, corrupt or foreign one is reported through the warning callback and skipped. */
    @NotNull
    public List<Map.Entry<Path, TransactionJournal>> loadAll() throws IOException {
        List<Map.Entry<Path, TransactionJournal>> loaded = new ArrayList<>();
        for (Path path : journals()) {
            try {
                loaded.add(new AbstractMap.SimpleImmutableEntry<>(path, load(path)));
            } catch (Exception error) {
                warn.accept("Журнал обновления " + path + " нечитаем и пропущен: "
                    + (error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName()));
            }
        }
        return loaded;
    }

    /** Loads the journal at {@code path} after checking its location, size, and contents. */
    @NotNull
    public TransactionJournal load(@NotNull Path path) throws IOException {
        Path normalized = checkedPath(path, root);
        if (!normalized.getFileName().toString().equals("journal.json")
            || !normalized.getParent().getParent().equals(root.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Invalid transaction journal location");
        }
        if (Files.size(normalized) > MAX_JOURNAL_BYTES) {
            throw new IllegalArgumentException("Transaction journal exceeds size limit");
        }
        TransactionJournal journal = TransactionJournal.load(normalized);
        validate(normalized, journal);
        return journal;
    }

    /** Rejects journals whose identifiers, versions, digests, or paths are malformed or escape their roots. */
    public void validate(@NotNull Path path, @NotNull TransactionJournal journal) throws IOException {
        Path normalized = checkedPath(path, root);
        require(journal.getId().equals(normalized.getParent().getFileName().toString()), "Transaction ID does not match its directory");
        require(!journal.getArtifacts().isEmpty(), "Transaction has no artifacts");
        validateMetadata(journal);
        List<Path> targets = new ArrayList<>();
        for (JournalArtifact record : journal.getArtifacts()) {
            validateArtifact(normalized.getParent(), record);
            targets.add(checkedPath(Paths.get(record.getTarget()), publicationRoot));
        }
        require(new HashSet<>(targets).size() == targets.size(), "Transaction contains duplicate targets");
    }

    /** Writes the journal durably. */
    public void save(@NotNull Path path, @NotNull TransactionJournal journal) throws IOException {
        TransactionJournal.save(path, journal);
    }

    /** Moves the journal to {@code state} and persists it. */
    public void transition(@NotNull Path path, @NotNull TransactionJournal journal, @NotNull TransactionState state)
        throws IOException {
        journal.setState(state);
        save(path, journal);
    }

    /** Returns whether every replaced target still has an intact backup. */
    public boolean hasRollbackData(@NotNull TransactionJournal journal) throws IOException {
        for (JournalArtifact record : journal.getArtifacts()) {
            if (record.getTargetExisted() && !hasIntactBackup(record)) {
                return false;
            }
        }
        return true;
    }

    /** Like {@link #hasRollbackData}, but an unreadable backup file is reported through the warning callback and counts as missing. */
    public boolean hasReadableRollbackData(@NotNull Path path, @NotNull TransactionJournal journal) {
        try {
            return hasRollbackData(journal);
        } catch (IOException error) {
            warn.accept("Резервные файлы набора " + path + " нечитаемы: "
                + (error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName()));
            return false;
        }
    }

    /** SHA-256 of the file at {@code path}. */
    @NotNull
    public String digest(@NotNull Path path) throws IOException {
        return Digests.sha256(path);
    }

    /** Normalizes {@code path} and requires it to stay inside {@code allowedRoot}. */
    @NotNull
    public Path checkedPath(@NotNull Path path, @NotNull Path allowedRoot) throws IOException {
        return UpdatePaths.inside(path, allowedRoot);
    }

    private boolean hasIntactBackup(JournalArtifact record) throws IOException {
        Path backup = Paths.get(record.getBackup());
        if (!Files.isRegularFile(backup)) {
            return false;
        }
        String expected = record.getBackupSha256() != null ? record.getBackupSha256()
            : record.getPrior() == null ? null : record.getPrior().getSha256();
        return expected == null || digest(backup).equals(expected);
    }

    private void validateMetadata(TransactionJournal journal) {
        if (journal.getCreatedAt() != null) {
            Instant.parse(journal.getCreatedAt());
        }
        if (journal.getPlanId() != null) {
            UUID.fromString(journal.getPlanId());
        }
        require(journal.getTargetApi() == null || journal.getTargetApi() > 0, "Invalid target API");
        if (journal.getConfigurationSnapshot() != null) {
            UUID.fromString(journal.getConfigurationSnapshot());
        }
        if (journal.getRollbackSession() != null) {
            UUID.fromString(journal.getRollbackSession());
        }
        if (journal.getHealthCheckedAt() != null) {
            Instant.parse(journal.getHealthCheckedAt());
        }
        String purpose = journal.getPurpose();
        require(purpose == null || purpose.equals("STAGE") || purpose.equals("INSTALL") || purpose.equals("RESTORE"),
            "Invalid transaction purpose");
        if (journal.getParentTransaction() != null) {
            UUID.fromString(journal.getParentTransaction());
        }
    }

    private void validateArtifact(Path directory, JournalArtifact record) throws IOException {
        checkedPath(Paths.get(record.getBackup()), directory);
        checkedPath(Paths.get(record.getStaged()), directory);
        if (record.getInstalledTarget() != null) {
            checkedPath(Paths.get(record.getInstalledTarget()), publicationRoot);
        }
        require(isDigestOrNull(record.getBackupSha256()), "Invalid backup digest");
        for (JournalVersion version : new JournalVersion[] {record.getExpected(), record.getPrior()}) {
            if (version != null) {
                SemanticVersion.parse(version.getVersion());
                new ApiVersionRange(version.getMinimumApi(), version.getMaximumApi());
                require(isDigestOrNull(version.getSha256()), "Invalid persisted artifact digest");
            }
        }
    }

    private static boolean isDigestOrNull(String value) {
        return value == null || SHA256.matcher(value).matches();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
