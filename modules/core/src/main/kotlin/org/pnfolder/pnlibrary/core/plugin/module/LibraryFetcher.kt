package org.pnfolder.pnlibrary.core.plugin.module

import org.pnfolder.pnlibrary.api.libraries.LibraryScope
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary
import org.pnfolder.pnlibrary.spi.libraries.ExternalLibraries

/** Downloads one library for [owner]; tests replace it, the runtime asks the Libby loader of the platform. */
internal fun interface LibraryFetcher {
    /** Returns the class loader that holds an isolated library, or `null` for any other scope. */
    fun fetch(loaderOwner: ClassLoader, owner: String, plugin: Any?, library: ModuleLibrary): ClassLoader?

    companion object {
        /**
         * Tries every place in [ModuleLibrary.allSources] in turn, as many as there are. A source is checked against its own
         * sum, or not at all when it has none. The first one that gives a jar wins; when none does, the error lists the
         * reason of each.
         */
        val THROUGH_LIBBY = LibraryFetcher { loader, owner, plugin, library ->
            val failures = mutableListOf<String>()
            for (source in SourceHealth.order(library.allSources)) {
                try {
                    return@LibraryFetcher ExternalLibraries.load(
                        loader, owner, library.group, library.artifact, library.version, library.classifier,
                        source.sha256,
                        if (source.direct) source.url else null, if (source.direct) emptyList() else listOf(source.url),
                        library.scope == LibraryScope.ISOLATED, plugin.takeIf { library.scope == LibraryScope.PLUGIN },
                    )
                } catch (failure: IllegalStateException) {
                    SourceHealth.failed(source.url)
                    failures += "${source.name}: ${failure.message}"
                }
            }
            throw IllegalStateException("Cannot load ${library.coordinates}: ${failures.joinToString("; ").ifEmpty { "no source to look in" }}")
        }
    }
}

/**
 * Remembers, in memory only, which places failed in the last few minutes, so the next library does not wait on a dead mirror
 * first: such a place is tried last. The order the author wrote is kept otherwise, and nothing is saved to disk.
 */
internal object SourceHealth {
    private const val MEMORY_MILLIS = 5 * 60_000L
    private val failedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun failed(url: String, now: Long = System.currentTimeMillis()) {
        failedAt[url] = now
    }

    /** [sources] in their own order, except that places that failed within the last five minutes go to the end. */
    fun order(sources: List<org.pnfolder.pnlibrary.api.libraries.LibrarySource>, now: Long = System.currentTimeMillis()):
        List<org.pnfolder.pnlibrary.api.libraries.LibrarySource> {
        val (bad, good) = sources.partition { (failedAt[it.url] ?: Long.MIN_VALUE).let { at -> at != Long.MIN_VALUE && now - at < MEMORY_MILLIS } }
        return good + bad
    }

    fun forgetAll() = failedAt.clear()
}
