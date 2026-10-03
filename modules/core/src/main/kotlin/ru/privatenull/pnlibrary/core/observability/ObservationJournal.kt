package ru.privatenull.pnlibrary.core.observability

import com.google.gson.GsonBuilder
import ru.privatenull.pnlibrary.api.observability.Observation
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.ArrayDeque

internal class ObservationJournal(dataFolder: Path) : AutoCloseable {
    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val lock = Any()
    private val observations = ArrayDeque<Observation>()
    private val file = dataFolder.resolve("observability").resolve("observations.jsonl")

    init {
        Files.createDirectories(file.parent)
        load()
    }

    fun append(observation: Observation) = synchronized(lock) {
        val persisted = observation.copy(files = emptyList())
        observations.addLast(persisted)
        Files.writeString(
            file,
            gson.toJson(persisted) + "\n",
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }

    fun recent(): List<Observation> = synchronized(lock) { observations.toList() }

    private fun load() = synchronized(lock) {
        if (!Files.isRegularFile(file)) return
        Files.readAllLines(file, StandardCharsets.UTF_8)
            .mapNotNull { line -> runCatching { gson.fromJson(line, Observation::class.java) }.getOrNull() }
            .forEach(observations::addLast)
    }

    override fun close() = Unit
}
