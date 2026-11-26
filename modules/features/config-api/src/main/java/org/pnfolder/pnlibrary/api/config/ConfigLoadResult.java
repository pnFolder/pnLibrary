package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of loading and synchronizing one code-first configuration file.
 *
 * <p>All path collections use full dot-separated YAML paths and are empty when that synchronization phase made no
 * changes.
 */
public final class ConfigLoadResult<T> {
    private final T value;
    private final List<String> addedPaths;
    private final File backup;
    private final List<String> removedPaths;
    private final List<String> addedComments;
    private final List<String> appliedMigrations;

    /** Creates a result without removed paths, added comments or applied migrations. */
    public ConfigLoadResult(T value, @NotNull List<String> addedPaths, @Nullable File backup) {
        this(value, addedPaths, backup, Collections.<String>emptyList(), Collections.<String>emptyList(),
                Collections.<String>emptyList());
    }

    /**
     * Creates a result.
     *
     * @param value validated typed value now held by the managed configuration
     * @param addedPaths missing default values inserted into the document
     * @param backup backup of the original file, or {@code null} when no backup was needed or enabled
     * @param removedPaths unknown YAML values removed according to {@link UnknownValuePolicy#REMOVE}
     * @param addedComments code-defined comments inserted into the document
     * @param appliedMigrations ordered migration labels applied before decoding
     */
    public ConfigLoadResult(T value, @NotNull List<String> addedPaths, @Nullable File backup,
                            @NotNull List<String> removedPaths, @NotNull List<String> addedComments,
                            @NotNull List<String> appliedMigrations) {
        this.value = value;
        this.addedPaths = addedPaths;
        this.backup = backup;
        this.removedPaths = removedPaths;
        this.addedComments = addedComments;
        this.appliedMigrations = appliedMigrations;
    }

    /** Validated typed value now held by the managed configuration. */
    public T getValue() {
        return value;
    }

    /** Missing default values inserted into the document. */
    @NotNull
    public List<String> getAddedPaths() {
        return addedPaths;
    }

    /** Backup of the original file, or {@code null} when no backup was needed or enabled. */
    @Nullable
    public File getBackup() {
        return backup;
    }

    /** Unknown YAML values removed according to {@link UnknownValuePolicy#REMOVE}. */
    @NotNull
    public List<String> getRemovedPaths() {
        return removedPaths;
    }

    /** Code-defined comments inserted into the document. */
    @NotNull
    public List<String> getAddedComments() {
        return addedComments;
    }

    /** Ordered migration labels applied before decoding. */
    @NotNull
    public List<String> getAppliedMigrations() {
        return appliedMigrations;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConfigLoadResult)) {
            return false;
        }
        ConfigLoadResult<?> that = (ConfigLoadResult<?>) other;
        return Objects.equals(value, that.value) && addedPaths.equals(that.addedPaths) && Objects.equals(backup, that.backup)
                && removedPaths.equals(that.removedPaths) && addedComments.equals(that.addedComments)
                && appliedMigrations.equals(that.appliedMigrations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, addedPaths, backup, removedPaths, addedComments, appliedMigrations);
    }

    @Override
    public String toString() {
        return "ConfigLoadResult(value=" + value + ", addedPaths=" + addedPaths + ", backup=" + backup
                + ", removedPaths=" + removedPaths + ", addedComments=" + addedComments + ", appliedMigrations="
                + appliedMigrations + ")";
    }
}
