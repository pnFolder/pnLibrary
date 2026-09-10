package ru.privatenull.pnlibrary.api.config

/** What to do when the configuration file does not exist. */
enum class MissingFilePolicy { CREATE, FAIL }

/** What to do with fields present in code defaults but absent from YAML. */
enum class MissingValuePolicy { ADD, USE_DEFAULT, FAIL }

/** What to do with YAML keys that no longer exist in the typed model. */
enum class UnknownValuePolicy { PRESERVE, REMOVE, FAIL }

/** What to do when an existing field has no code-defined comment. */
enum class CommentPolicy { PRESERVE, ADD_MISSING }

/** Immutable synchronization behavior for one YAML file. */
data class ConfigOptions @JvmOverloads constructor(
    val missingFile: MissingFilePolicy = MissingFilePolicy.CREATE,
    val missingValues: MissingValuePolicy = MissingValuePolicy.ADD,
    val unknownValues: UnknownValuePolicy = UnknownValuePolicy.PRESERVE,
    val comments: CommentPolicy = CommentPolicy.ADD_MISSING,
    val backups: Boolean = true,
) {
    companion object {
        @JvmField val DEFAULT = ConfigOptions()
        @JvmStatic fun builder() = Builder()
    }

    class Builder {
        private var missingFile = MissingFilePolicy.CREATE
        private var missingValues = MissingValuePolicy.ADD
        private var unknownValues = UnknownValuePolicy.PRESERVE
        private var comments = CommentPolicy.ADD_MISSING
        private var backups = true
        fun missingFile(value: MissingFilePolicy) = apply { missingFile = value }
        fun missingValues(value: MissingValuePolicy) = apply { missingValues = value }
        fun unknownValues(value: UnknownValuePolicy) = apply { unknownValues = value }
        fun comments(value: CommentPolicy) = apply { comments = value }
        fun backups(value: Boolean) = apply { backups = value }
        fun build() = ConfigOptions(missingFile, missingValues, unknownValues, comments, backups)
    }
}
