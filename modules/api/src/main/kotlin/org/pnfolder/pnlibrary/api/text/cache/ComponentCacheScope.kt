package org.pnfolder.pnlibrary.api.text.cache

/** Ownership boundary used for parsed-component cache entries. */
enum class ComponentCacheScope {
    /** Bypasses the cache and parses every request. */
    NONE,

    /** Isolates entries by the plugin that owns the component service. */
    PLUGIN,

    /** Allows compatible service instances to share cached entries. */
    GLOBAL,
}
