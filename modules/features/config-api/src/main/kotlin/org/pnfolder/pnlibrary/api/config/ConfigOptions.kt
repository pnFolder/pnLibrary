package org.pnfolder.pnlibrary.api.config

/** What to do when the configuration file does not exist. */
enum class MissingFilePolicy {
    /** Serialize the code-defined defaults to a new file, then load them. */
    CREATE,

    /** Abort loading instead of creating a file. */
    FAIL,
}

/** What to do with fields present in code defaults but absent from YAML. */
enum class MissingValuePolicy {
    /** Insert missing fields into YAML and persist the synchronized document. */
    ADD,

    /** Use code defaults in memory without adding the missing fields to YAML. */
    USE_DEFAULT,

    /** Reject the file when a model field is missing. */
    FAIL,
}

/** What to do with YAML keys that no longer exist in the typed model. */
enum class UnknownValuePolicy {
    /** Keep unknown keys when the file is synchronized. */
    PRESERVE,

    /** Delete unknown keys during synchronization. */
    REMOVE,

    /** Reject the file when at least one unknown key is present. */
    FAIL,
}

/** What to do with a list element whose `type` is not known to a polymorphic type. */
enum class UnknownTypePolicy {
    /** Reject the whole file with an error that names the element. */
    FAIL,

    /**
     * Skip the element, warn, and keep it in the file untouched. While an element is skipped the library does not
     * rewrite the file from memory (`save`, `update`, `resetToDefaults` fail), so nothing is lost. When the type
     * appears later, the file is read again and the element comes back. Applies to list elements.
     */
    SKIP,
}

/** What to do when an existing field has no code-defined comment. */
enum class CommentPolicy {
    /** Leave the file's existing comments unchanged. */
    PRESERVE,

    /** Add missing [ConfigComment] text while retaining existing comments. */
    ADD_MISSING,
}

/**
 * Immutable synchronization behavior for one YAML file.
 *
 * The default favors code-first configuration: missing files and values are created, unknown
 * values are retained, missing code comments are inserted, and a backup is created before a
 * destructive rewrite. Use [builder] from Java or named constructor arguments from Kotlin.
 */
data class ConfigOptions @JvmOverloads constructor(
    /** Policy applied when the target file does not exist. */
    val missingFile: MissingFilePolicy = MissingFilePolicy.CREATE,
    /** Policy applied to model fields absent from an existing document. */
    val missingValues: MissingValuePolicy = MissingValuePolicy.ADD,
    /** Policy applied to document keys absent from the model. */
    val unknownValues: UnknownValuePolicy = UnknownValuePolicy.PRESERVE,
    /** Policy controlling synchronization of code-defined comments. */
    val comments: CommentPolicy = CommentPolicy.ADD_MISSING,
    /** Whether pnLibrary may create a backup before rewriting an existing file. */
    val backups: Boolean = true,
    /** Numbered steps that bring older files up to this code's version. Use `null`, or `Builder.withoutVersion()`, to omit the marker. */
    val migrations: ConfigSteps<*>? = ConfigSteps.NONE,
    /** Default conversion from source field names to YAML keys. */
    val naming: ConfigNamingStrategy = ConfigNamingStrategy.AS_DECLARED,
    /** Policy applied to list elements whose `type` no registered implementation accepts. */
    val unknownTypes: UnknownTypePolicy = UnknownTypePolicy.FAIL,
    /**
     * Folder the path is counted from. In [ConfigRoot.PLUGINS] and [ConfigRoot.SERVER] the file is somebody else's: it is
     * never created, a backup is always made before a rewrite and unknown keys are kept, whatever the other options say.
     * [ConfigRoot.EXTERNAL] (see `ConfigScope.yamlAt`) is an absolute path of the plugin's own choice, without those limits.
     */
    val root: ConfigRoot = ConfigRoot.PLUGIN,
    /** Only read the file: nothing is created, synchronized or written, and `save` fails. Several plugins may read one file. */
    val readOnly: Boolean = false,
) {
    /** Default options and the Java-friendly fluent construction entry point. */
    companion object {
        /** Shared default option set. */
        @JvmField
        val DEFAULT = ConfigOptions()

        /** Creates a Java-friendly fluent builder initialized with the defaults. */
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    /** Java-friendly mutable builder for [ConfigOptions]. */
    class Builder internal constructor() {
        private var missingFile = MissingFilePolicy.CREATE
        private var missingValues = MissingValuePolicy.ADD
        private var unknownValues = UnknownValuePolicy.PRESERVE
        private var comments = CommentPolicy.ADD_MISSING
        private var backups = true
        private var migrations: ConfigSteps<*>? = ConfigSteps.NONE
        private var naming = ConfigNamingStrategy.AS_DECLARED
        private var unknownTypes = UnknownTypePolicy.FAIL
        private var root = ConfigRoot.PLUGIN
        private var readOnly = false
        /** Sets the missing-file policy. */
        fun missingFile(value: MissingFilePolicy): Builder = apply { missingFile = value }

        /** Sets the missing-value policy. */
        fun missingValues(value: MissingValuePolicy): Builder = apply { missingValues = value }

        /** Sets the unknown-value policy. */
        fun unknownValues(value: UnknownValuePolicy): Builder = apply { unknownValues = value }

        /** Sets the comment synchronization policy. */
        fun comments(value: CommentPolicy): Builder = apply { comments = value }

        /** Enables or disables backups before rewrites. */
        fun backups(value: Boolean): Builder = apply { backups = value }

        /** Attaches a versioned migration plan. */
        fun migrations(value: ConfigSteps<*>): Builder = apply { migrations = value }

        /** Omits the version marker from the file; use only for files that must stay marker-free. */
        fun withoutVersion(): Builder = apply { migrations = null }

        /** Sets the default field naming strategy. */
        fun naming(value: ConfigNamingStrategy): Builder = apply { naming = value }

        /** Sets what happens to list elements with an unknown `type`. */
        fun unknownTypes(value: UnknownTypePolicy): Builder = apply { unknownTypes = value }

        /** Sets the folder the path is counted from. */
        fun root(value: ConfigRoot): Builder = apply { root = value }

        /** Makes the file read-only for the library. */
        fun readOnly(value: Boolean = true): Builder = apply { readOnly = value }

        /** Creates the immutable option set. */
        fun build(): ConfigOptions =
            ConfigOptions(missingFile, missingValues, unknownValues, comments, backups, migrations, naming, unknownTypes, root, readOnly)
    }
}
