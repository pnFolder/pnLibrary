package org.pnfolder.pnlibrary.updates.engine.transaction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Transaction awaiting post-restart health confirmation. */
public final class PendingTransaction {
    private final Path journal;
    private final Map<String, String> expectedVersions;
    private final String preparedSession;
    private final Set<String> unknownProducts;
    private final boolean rollback;

    public PendingTransaction(@NotNull Path journal, @NotNull Map<String, String> expectedVersions) {
        this(journal, expectedVersions, null, Collections.emptySet(), false);
    }

    public PendingTransaction(@NotNull Path journal, @NotNull Map<String, String> expectedVersions,
                              @Nullable String preparedSession, @NotNull Set<String> unknownProducts) {
        this(journal, expectedVersions, preparedSession, unknownProducts, false);
    }

    /**
     * @param journal durable transaction journal path
     * @param expectedVersions expected product versions keyed by normalized product ID
     * @param preparedSession process session that prepared the transaction, when known
     * @param unknownProducts products whose version cannot be checked
     * @param rollback whether this is a rollback rather than an update
     */
    public PendingTransaction(@NotNull Path journal, @NotNull Map<String, String> expectedVersions,
                              @Nullable String preparedSession, @NotNull Set<String> unknownProducts, boolean rollback) {
        this.journal = Objects.requireNonNull(journal, "journal");
        this.expectedVersions = Collections.unmodifiableMap(new LinkedHashMap<>(expectedVersions));
        this.preparedSession = preparedSession;
        this.unknownProducts = Collections.unmodifiableSet(new LinkedHashSet<>(unknownProducts));
        this.rollback = rollback;
    }

    @NotNull
    public Path getJournal() {
        return journal;
    }

    @NotNull
    public Map<String, String> getExpectedVersions() {
        return expectedVersions;
    }

    @Nullable
    public String getPreparedSession() {
        return preparedSession;
    }

    @NotNull
    public Set<String> getUnknownProducts() {
        return unknownProducts;
    }

    public boolean getRollback() {
        return rollback;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PendingTransaction)) {
            return false;
        }
        PendingTransaction that = (PendingTransaction) other;
        return rollback == that.rollback && journal.equals(that.journal) && expectedVersions.equals(that.expectedVersions)
            && Objects.equals(preparedSession, that.preparedSession) && unknownProducts.equals(that.unknownProducts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(journal, expectedVersions, preparedSession, unknownProducts, rollback);
    }

    @Override
    public String toString() {
        return "PendingTransaction(journal=" + journal + ", expectedVersions=" + expectedVersions
            + ", preparedSession=" + preparedSession + ", unknownProducts=" + unknownProducts + ", rollback=" + rollback + ")";
    }
}
