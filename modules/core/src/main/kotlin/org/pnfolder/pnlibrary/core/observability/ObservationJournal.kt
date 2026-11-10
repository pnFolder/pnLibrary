package org.pnfolder.pnlibrary.core.observability

import com.google.gson.GsonBuilder
import org.pnfolder.pnlibrary.api.observability.Observation
import org.pnfolder.pnlibrary.common.io.TextFiles
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.ArrayDeque

/**
 * Append-only JSON Lines journal of observations that keeps only what retention allows.
 *
 * Expired observations are cut from the file once a minute or when the list outgrows its bound by a margin,
 * not on every append; [recent] never shows an expired one meanwhile.
 *
 * @param retain keeps the observations that are still active at a time; replaceable so tests can count its use
 */
internal class ObservationJournal(
    dataFolder: Path,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retain: (Collection<Observation>, Long) -> List<Observation> = ObservationRetention::active,
) : AutoCloseable {
    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val lock = Any()
    private val observations = ArrayDeque<Observation>()
    private val file = dataFolder.resolve("observability").resolve("observations.jsonl")
    private var writer: Writer? = null
    private var lastRetentionAt: Long? = null

    init {
        Files.createDirectories(file.parent)
        load()
    }

    fun append(observation: Observation) = synchronized(lock) {
        observations.addLast(observation)
        val out = writer ?: Files.newBufferedWriter(file, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND).also { writer = it }
        out.write(gson.toJson(observation))
        out.write("\n")
        out.flush()
        val now = clock()
        val last = lastRetentionAt
        if (observations.size > ObservationRetention.MAX_OBSERVATIONS + OVERFLOW_MARGIN ||
            last == null || now - last >= RETENTION_INTERVAL_MILLIS || now < last
        ) {
            lastRetentionAt = now
            val active = retain(observations, now)
            if (active.size != observations.size) replaceAndRewrite(active)
        }
    }

    fun recent(): List<Observation> = synchronized(lock) { retain(observations, clock()) }

    fun clear() = synchronized(lock) {
        closeWriter()
        observations.clear()
        Files.deleteIfExists(file)
    }

    fun applyRetention(now: Long): Set<String> = synchronized(lock) {
        val active = retain(observations, now)
        replaceAndRewrite(active)
        active.mapTo(linkedSetOf()) { observation -> observation.id }
    }

    private fun replaceAndRewrite(active: List<Observation>) {
        observations.clear()
        observations.addAll(active)
        rewrite()
    }

    private fun rewrite() {
        closeWriter()
        if (observations.isEmpty()) {
            Files.deleteIfExists(file)
            return
        }
        val content = observations.joinToString(separator = "\n", postfix = "\n") { gson.toJson(it) }
        TextFiles.write(file, content, StandardCharsets.UTF_8)
    }

    private fun load() = synchronized(lock) {
        if (!Files.isRegularFile(file)) return
        Files.readAllLines(file, StandardCharsets.UTF_8)
            .mapNotNull { line -> runCatchingNonFatal { gson.fromJson(line, Observation::class.java) }.getOrNull() }
            .forEach(observations::addLast)
    }

    private fun closeWriter() {
        val open = writer ?: return
        writer = null
        open.close()
    }

    override fun close() = synchronized(lock) { closeWriter() }

    private companion object {
        const val RETENTION_INTERVAL_MILLIS = 60_000L

        /** How far past the bound the list may grow before it is cut at once, so a full journal is not rewritten per append. */
        const val OVERFLOW_MARGIN = 500
    }
}
