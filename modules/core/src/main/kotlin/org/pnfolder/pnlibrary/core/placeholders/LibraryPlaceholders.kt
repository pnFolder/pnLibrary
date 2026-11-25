package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity

/** The numbers the library counts about itself: `{pnlibrary.stats.errors}` and the rest. */
class LibraryStatistics(private val statistics: () -> Map<String, Long>) {
    @Placeholder("Errors since start")
    fun errors(): Reply<Long> = Reply.of(statistics()["errors"] ?: 0L)

    @Placeholder("Warnings since start")
    fun warnings(): Reply<Long> = Reply.of(statistics()["warnings"] ?: 0L)

    @Placeholder("Tasks run since start")
    fun tasks(): Reply<Long> = Reply.of(statistics()["tasks"] ?: 0L)

    @Placeholder("Tasks that failed")
    fun failedTasks(): Reply<Long> = Reply.of(statistics()["tasks.failed"] ?: 0L)

    @Placeholder("Events handled")
    fun events(): Reply<Long> = Reply.of(statistics()["events"] ?: 0L)
}

/** What the server is: `{pnlibrary.server.platform}`. */
class LibraryServer(private val platform: PlatformIdentity) {
    @Placeholder("Platform name")
    fun platform(): Reply<String> = Reply.of(platform.type.displayName)

    @Placeholder("Server software")
    fun implementation(): Reply<String> = Reply.of(platform.implementationName)

    @Placeholder("Whether this is a proxy")
    fun proxy(): Reply<Boolean> = Reply.of(platform.isProxy)

    @Placeholder("Java version")
    fun java(): Reply<String> = Reply.of(System.getProperty("java.version", "unknown"))
}

/** The library's own placeholders, `{pnlibrary.*}`, readable by every plugin. */
@Placeholders("pnlibrary")
class LibraryPlaceholders(
    private val platform: PlatformIdentity,
    private val startedAtMillis: Long,
    private val pluginCount: () -> Int,
    statistics: () -> Map<String, Long>,
) {
    private val stats = LibraryStatistics(statistics)
    private val serverInfo = LibraryServer(platform)

    @Placeholder("Library version")
    fun version(): Reply<String> = Reply.of(PnLibraryIdentity.VERSION)

    @Placeholder("Registered plugins")
    fun plugins(): Reply<Int> = Reply.of(pluginCount())

    @Placeholder("Seconds since the library started", )
    fun uptime(): Reply<Long> = Reply.of((System.currentTimeMillis() - startedAtMillis) / MILLIS_PER_SECOND)

    @Placeholder("Counters")
    fun stats(): Reply<LibraryStatistics> = Reply.next(stats)

    @Placeholder("The server")
    fun server(): Reply<LibraryServer> = Reply.next(serverInfo)

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
