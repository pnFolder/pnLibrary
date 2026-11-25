package org.pnfolder.pnlibrary.api.placeholders.annotation

/** What a placeholder shows when its method has no value (returns `null`). */
enum class Invalid {
    /** Leave the address in the text as it is. This is the default. */
    KEEP,

    /** Show [Placeholder.default]. Only for text placeholders. */
    DEFAULT,

    /** Show nothing. Only for text placeholders. */
    EMPTY,
}
