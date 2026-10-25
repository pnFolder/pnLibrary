package org.pnfolder.pnlibrary.api.placeholders

/** Values included when constructing a placeholder cache key. */
enum class PlaceholderCacheScope {
    /** Disables result caching. */
    NONE,
    /** Shares one cached result across all invocations. */
    GLOBAL,
    /** Isolates results by the consuming plugin. */
    PLUGIN,
    /** Isolates results by player identity. */
    PLAYER,
    /** Isolates results by parsed placeholder arguments. */
    ARGUMENTS,
}
