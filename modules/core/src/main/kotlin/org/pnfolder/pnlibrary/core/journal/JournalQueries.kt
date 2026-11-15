package org.pnfolder.pnlibrary.core.journal

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.journal.JournalDetail
import org.pnfolder.pnlibrary.core.journal.JournalGroup
import java.time.Duration

/** What the menu and the join notice read from the journal: equal events merged, one event with its details. */
class JournalQueries(
    private val journal: JournalStore,
    private val config: PnLibraryConfig,
    private val startedAtMillis: Long,
    private val eventsCommand: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val noticeLock = Any()
    private var noticeBuiltAt: Long? = null
    private var notice: List<Component> = emptyList()

    /**
     * The lines shown to an administrator who joins, empty when the journal cannot be read. Several joins within
     * [NOTICE_TTL_MILLIS] share one journal read, so a wave of administrators does not rescan the files.
     */
    fun joinNotice(): List<Component> = synchronized(noticeLock) {
        val now = clock()
        val builtAt = noticeBuiltAt
        if (builtAt == null || now - builtAt >= NOTICE_TTL_MILLIS || now < builtAt) {
            notice = buildJoinNotice(now)
            noticeBuiltAt = now
        }
        notice
    }

    private fun buildJoinNotice(now: Long): List<Component> {
        val since = now - Duration.ofHours(config.journalJoinNoticeHours.toLong()).toMillis()
        return runCatchingNonFatal {
            joinDigest(journal.read(sinceMillis = since, limit = SCAN), now, config.journalJoinNoticeHours, startedAtMillis, eventsCommand)
        }.getOrDefault(emptyList())
    }

    /** Events of the last [sinceHours] hours, equal ones merged, newest first; [level] keeps one group of levels. */
    fun groups(level: String?, sinceHours: Int, limit: Int): List<JournalGroup> {
        val since = System.currentTimeMillis() - Duration.ofHours(sinceHours.toLong()).toMillis()
        val levels = JournalLevel.values().filter { level == null || it.fileGroup == level }.toSet()
        return runCatchingNonFatal {
            journal.read(levels = levels, sinceMillis = since, limit = SCAN)
                .groupBy { it.fingerprint }
                .map { (id, group) ->
                    val newest = group.maxByOrNull { it.timeMillis }!!
                    JournalGroup(id, newest.level.fileGroup, newest.source, newest.message.lineSequence().first(), group.size, newest.timeMillis)
                }
                .sortedByDescending { it.lastMillis }
                .take(limit)
        }.getOrDefault(emptyList())
    }

    /** The newest record of the event [id] with every time it happened, or null when it left the journal. */
    fun detail(id: String): JournalDetail? = runCatchingNonFatal {
        val records = journal.read(limit = SCAN).filter { it.fingerprint == id }
        val newest = records.maxByOrNull { it.timeMillis } ?: return@runCatchingNonFatal null
        JournalDetail(
            id = id,
            level = newest.level.fileGroup,
            source = newest.source,
            message = newest.message,
            stackTrace = newest.details[JournalRecord.STACKTRACE],
            details = newest.details - JournalRecord.STACKTRACE,
            times = records.map { it.timeMillis }.sortedDescending().take(TIMES),
        )
    }.getOrNull()

    private companion object {
        const val SCAN = 5_000
        const val TIMES = 50
        const val NOTICE_TTL_MILLIS = 45_000L
    }
}
