package ru.privatenull.pnlibrary.api.config

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

/** What to do when an existing field has no code-defined comment. */
enum class CommentPolicy {
    /** Leave the file's existing comments unchanged. */
    PRESERVE,

    /** Add missing [ConfigComment] text while retaining existing comments. */
    ADD_MISSING,
}

/** Converts Java/Kotlin field names into YAML keys. [ConfigKey] always wins. */
enum class ConfigNamingStrategy {
    /** Preserve the field name exactly as declared in source. */
    AS_DECLARED,

    /** Write `connectionTimeout` style keys. */
    CAMEL_CASE,

    /** Write `connection_timeout` style keys. */
    SNAKE_CASE,

    /** Write `connection-timeout` style keys. */
    KEBAB_CASE,

    /** Write `CONNECTION_TIMEOUT` style keys. */
    UPPER_SNAKE_CASE,
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
    /** Numbered steps that bring older files up to this code's version. Use `null` with [withoutVersion] to omit the marker. */
    val migrations: ConfigSteps<*>? = ConfigSteps.NONE,
    /** Default conversion from source field names to YAML keys. */
    val naming: ConfigNamingStrategy = ConfigNamingStrategy.AS_DECLARED,
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
    class Builder {
        private var missingFile = MissingFilePolicy.CREATE
        private var missingValues = MissingValuePolicy.ADD
        private var unknownValues = UnknownValuePolicy.PRESERVE
        private var comments = CommentPolicy.ADD_MISSING
        private var backups = true
        private var migrations: ConfigSteps<*>? = ConfigSteps.NONE
        private var naming = ConfigNamingStrategy.AS_DECLARED
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

        /** Creates the immutable option set. */
        fun build(): ConfigOptions = ConfigOptions(missingFile, missingValues, unknownValues, comments, backups, migrations, naming)
    }
}
