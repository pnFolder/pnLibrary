package org.pnfolder.pnlibrary.api.plugin

/** A row pnLibrary adds by itself to the enabled and disabled cards; a plugin can leave any of them out. */
enum class LifecycleRow {
    /** The module identifier (enabled card). */
    IDENTIFIER,

    /** The platform the module runs on (enabled card). */
    PLATFORM,

    /** The metrics state (enabled and disabled cards). */
    METRICS,

    /** The update checker state (enabled and disabled cards). */
    UPDATES,

    /** The diagnostics state (enabled card). */
    DIAGNOSTICS,

    /** The PlaceholderAPI state (enabled card). */
    PLACEHOLDER_API,

    /** The released-resources state (disabled card). */
    RESOURCES,

    /** Every row of a feature the module did not configure, the ones that read "SKIP: not configured". */
    NOT_CONFIGURED,
}
