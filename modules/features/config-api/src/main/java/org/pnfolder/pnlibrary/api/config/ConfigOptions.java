package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Immutable synchronization behavior for one YAML file.
 *
 * <p>The default favors code-first configuration: missing files and values are created, unknown values are retained,
 * missing code comments are inserted, and a backup is created before a destructive rewrite. Use {@link #builder()} to
 * change single settings and {@link #toBuilder()} to derive a changed copy.
 */
public final class ConfigOptions {
    /** Shared default option set. */
    public static final ConfigOptions DEFAULT = new ConfigOptions();

    private final MissingFilePolicy missingFile;
    private final MissingValuePolicy missingValues;
    private final UnknownValuePolicy unknownValues;
    private final CommentPolicy comments;
    private final boolean backups;
    private final ConfigSteps<?> migrations;
    private final ConfigNamingStrategy naming;
    private final UnknownTypePolicy unknownTypes;
    private final ConfigRoot root;
    private final boolean readOnly;

    /** Creates the default option set. */
    public ConfigOptions() {
        this(MissingFilePolicy.CREATE, MissingValuePolicy.ADD, UnknownValuePolicy.PRESERVE, CommentPolicy.ADD_MISSING,
                true, ConfigSteps.NONE, ConfigNamingStrategy.AS_DECLARED, UnknownTypePolicy.FAIL, ConfigRoot.PLUGIN, false);
    }

    /**
     * Creates an option set.
     *
     * @param missingFile policy applied when the target file does not exist
     * @param missingValues policy applied to model fields absent from an existing document
     * @param unknownValues policy applied to document keys absent from the model
     * @param comments policy controlling synchronization of code-defined comments
     * @param backups whether pnLibrary may create a backup before rewriting an existing file
     * @param migrations numbered steps that bring older files up to this code's version, or {@code null} to omit the marker
     * @param naming default conversion from source field names to YAML keys
     * @param unknownTypes policy applied to list elements whose {@code type} no registered implementation accepts
     * @param root folder the path is counted from; in {@link ConfigRoot#PLUGINS} and {@link ConfigRoot#SERVER} the file
     *             is somebody else's: it is never created, a backup is always made before a rewrite and unknown keys
     *             are kept, whatever the other options say
     * @param readOnly only read the file: nothing is created, synchronized or written, and {@code save} fails
     */
    public ConfigOptions(@NotNull MissingFilePolicy missingFile, @NotNull MissingValuePolicy missingValues,
                         @NotNull UnknownValuePolicy unknownValues, @NotNull CommentPolicy comments, boolean backups,
                         @Nullable ConfigSteps<?> migrations, @NotNull ConfigNamingStrategy naming,
                         @NotNull UnknownTypePolicy unknownTypes, @NotNull ConfigRoot root, boolean readOnly) {
        this.missingFile = Objects.requireNonNull(missingFile, "missingFile");
        this.missingValues = Objects.requireNonNull(missingValues, "missingValues");
        this.unknownValues = Objects.requireNonNull(unknownValues, "unknownValues");
        this.comments = Objects.requireNonNull(comments, "comments");
        this.backups = backups;
        this.migrations = migrations;
        this.naming = Objects.requireNonNull(naming, "naming");
        this.unknownTypes = Objects.requireNonNull(unknownTypes, "unknownTypes");
        this.root = Objects.requireNonNull(root, "root");
        this.readOnly = readOnly;
    }

    /** Policy applied when the target file does not exist. */
    @NotNull
    public MissingFilePolicy getMissingFile() {
        return missingFile;
    }

    /** Policy applied to model fields absent from an existing document. */
    @NotNull
    public MissingValuePolicy getMissingValues() {
        return missingValues;
    }

    /** Policy applied to document keys absent from the model. */
    @NotNull
    public UnknownValuePolicy getUnknownValues() {
        return unknownValues;
    }

    /** Policy controlling synchronization of code-defined comments. */
    @NotNull
    public CommentPolicy getComments() {
        return comments;
    }

    /** Whether pnLibrary may create a backup before rewriting an existing file. */
    public boolean getBackups() {
        return backups;
    }

    /** Numbered steps that bring older files up to this code's version, or {@code null} when the marker is omitted. */
    @Nullable
    public ConfigSteps<?> getMigrations() {
        return migrations;
    }

    /** Default conversion from source field names to YAML keys. */
    @NotNull
    public ConfigNamingStrategy getNaming() {
        return naming;
    }

    /** Policy applied to list elements whose {@code type} no registered implementation accepts. */
    @NotNull
    public UnknownTypePolicy getUnknownTypes() {
        return unknownTypes;
    }

    /** Folder the path is counted from. */
    @NotNull
    public ConfigRoot getRoot() {
        return root;
    }

    /** Whether the file is only read: nothing is created, synchronized or written. */
    public boolean getReadOnly() {
        return readOnly;
    }

    /** Creates a fluent builder initialized with the defaults. */
    @NotNull
    public static Builder builder() {
        return new Builder(DEFAULT);
    }

    /** Creates a fluent builder initialized with the values of this option set. */
    @NotNull
    public Builder toBuilder() {
        return new Builder(this);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConfigOptions)) {
            return false;
        }
        ConfigOptions that = (ConfigOptions) other;
        return backups == that.backups && readOnly == that.readOnly && missingFile == that.missingFile
                && missingValues == that.missingValues && unknownValues == that.unknownValues
                && comments == that.comments && Objects.equals(migrations, that.migrations)
                && naming == that.naming && unknownTypes == that.unknownTypes && root == that.root;
    }

    @Override
    public int hashCode() {
        return Objects.hash(missingFile, missingValues, unknownValues, comments, backups, migrations, naming,
                unknownTypes, root, readOnly);
    }

    @Override
    public String toString() {
        return "ConfigOptions(missingFile=" + missingFile + ", missingValues=" + missingValues + ", unknownValues="
                + unknownValues + ", comments=" + comments + ", backups=" + backups + ", migrations=" + migrations
                + ", naming=" + naming + ", unknownTypes=" + unknownTypes + ", root=" + root + ", readOnly="
                + readOnly + ")";
    }

    /** Mutable builder for {@link ConfigOptions}. */
    public static final class Builder {
        private MissingFilePolicy missingFile;
        private MissingValuePolicy missingValues;
        private UnknownValuePolicy unknownValues;
        private CommentPolicy comments;
        private boolean backups;
        private ConfigSteps<?> migrations;
        private ConfigNamingStrategy naming;
        private UnknownTypePolicy unknownTypes;
        private ConfigRoot root;
        private boolean readOnly;

        Builder(ConfigOptions from) {
            this.missingFile = from.missingFile;
            this.missingValues = from.missingValues;
            this.unknownValues = from.unknownValues;
            this.comments = from.comments;
            this.backups = from.backups;
            this.migrations = from.migrations;
            this.naming = from.naming;
            this.unknownTypes = from.unknownTypes;
            this.root = from.root;
            this.readOnly = from.readOnly;
        }

        /** Sets the missing-file policy. */
        @NotNull
        public Builder missingFile(@NotNull MissingFilePolicy value) {
            missingFile = value;
            return this;
        }

        /** Sets the missing-value policy. */
        @NotNull
        public Builder missingValues(@NotNull MissingValuePolicy value) {
            missingValues = value;
            return this;
        }

        /** Sets the unknown-value policy. */
        @NotNull
        public Builder unknownValues(@NotNull UnknownValuePolicy value) {
            unknownValues = value;
            return this;
        }

        /** Sets the comment synchronization policy. */
        @NotNull
        public Builder comments(@NotNull CommentPolicy value) {
            comments = value;
            return this;
        }

        /** Enables or disables backups before rewrites. */
        @NotNull
        public Builder backups(boolean value) {
            backups = value;
            return this;
        }

        /** Attaches a versioned migration plan. */
        @NotNull
        public Builder migrations(@NotNull ConfigSteps<?> value) {
            migrations = value;
            return this;
        }

        /** Omits the version marker from the file; use only for files that must stay marker-free. */
        @NotNull
        public Builder withoutVersion() {
            migrations = null;
            return this;
        }

        /** Sets the default field naming strategy. */
        @NotNull
        public Builder naming(@NotNull ConfigNamingStrategy value) {
            naming = value;
            return this;
        }

        /** Sets what happens to list elements with an unknown {@code type}. */
        @NotNull
        public Builder unknownTypes(@NotNull UnknownTypePolicy value) {
            unknownTypes = value;
            return this;
        }

        /** Sets the folder the path is counted from. */
        @NotNull
        public Builder root(@NotNull ConfigRoot value) {
            root = value;
            return this;
        }

        /** Makes the file read-only for the library. */
        @NotNull
        public Builder readOnly() {
            return readOnly(true);
        }

        /** Makes the file read-only for the library, or writable again. */
        @NotNull
        public Builder readOnly(boolean value) {
            readOnly = value;
            return this;
        }

        /** Creates the immutable option set. */
        @NotNull
        public ConfigOptions build() {
            return new ConfigOptions(missingFile, missingValues, unknownValues, comments, backups, migrations, naming,
                    unknownTypes, root, readOnly);
        }
    }
}
