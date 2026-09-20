package ru.privatenull.pnlibrary.update

import com.google.gson.GsonBuilder
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.Paths
import java.util.UUID
import java.nio.file.StandardOpenOption.WRITE

enum class TransactionState {
    CREATED, DOWNLOADING, VERIFIED, STAGED, PUBLISHING, ACTIVATING, AWAITING_HEALTH,
    COMMITTED, ROLLING_BACK, ROLLED_BACK, FAILED,
}

data class TransactionArtifact(
    val specification: ArtifactSpecification,
    val source: Path,
    val target: Path,
)

data class TransactionResult(val state: TransactionState, val journal: Path)

data class JournalArtifact(
    val component: String,
    val target: String,
    val staged: String,
    val backup: String,
    val targetExisted: Boolean,
)

data class TransactionJournal(
    val id: String,
    var state: TransactionState,
    val artifacts: List<JournalArtifact>,
) {
    companion object {
        private val gson = GsonBuilder().setPrettyPrinting().create()

        fun load(path: Path): TransactionJournal = Files.newBufferedReader(path, StandardCharsets.UTF_8).use {
            gson.fromJson(it, TransactionJournal::class.java)
        }

        internal fun save(path: Path, journal: TransactionJournal) {
            val parent = path.toAbsolutePath().parent
            Files.createDirectories(parent)
            val temporary = Files.createTempFile(parent, path.fileName.toString(), ".tmp")
            try {
                Files.newBufferedWriter(temporary, StandardCharsets.UTF_8).use { gson.toJson(journal, it) }
                FileChannel.open(temporary, WRITE).use { it.force(true) }
                try {
                    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }
}

class UpdateTransaction(
    private val root: Path,
    private val verifier: ArtifactVerifier,
    publicationRoot: Path = root.toAbsolutePath().normalize().parent,
) {
    private val publicationRoot = publicationRoot.toAbsolutePath().normalize()

    fun apply(artifacts: List<TransactionArtifact>, healthCheck: () -> Boolean): TransactionResult {
        require(artifacts.isNotEmpty()) { "transaction must contain at least one artifact" }
        require(artifacts.map { it.specification.component }.distinct().size == artifacts.size) {
            "transaction contains duplicate components"
        }
        require(artifacts.map { it.target.toAbsolutePath().normalize() }.distinct().size == artifacts.size) {
            "transaction contains duplicate targets"
        }
        artifacts.forEach { artifact ->
            val target = artifact.target.toAbsolutePath().normalize()
            require(target.startsWith(publicationRoot)) {
                "publication target must stay inside $publicationRoot"
            }
        }

        val id = UUID.randomUUID().toString()
        val directory = root.toAbsolutePath().normalize().resolve(id)
        val staging = directory.resolve("staging")
        val backups = directory.resolve("backup")
        Files.createDirectories(staging)
        Files.createDirectories(backups)
        val records = artifacts.mapIndexed { index, artifact ->
            JournalArtifact(
                artifact.specification.component.value,
                artifact.target.toAbsolutePath().normalize().toString(),
                staging.resolve("$index-${artifact.target.fileName}").toString(),
                backups.resolve("$index-${artifact.target.fileName}").toString(),
                Files.exists(artifact.target),
            )
        }
        val journalPath = directory.resolve("journal.json")
        val journal = TransactionJournal(id, TransactionState.CREATED, records)
        TransactionJournal.save(journalPath, journal)
        var activationStarted = false
        try {
            artifacts.forEach { verifier.verify(it.source, it.specification) }
            transition(journalPath, journal, TransactionState.VERIFIED)
            artifacts.zip(records).forEach { (artifact, record) ->
                Files.copy(artifact.source, Paths.get(record.staged), StandardCopyOption.REPLACE_EXISTING)
            }
            transition(journalPath, journal, TransactionState.STAGED)
            artifacts.zip(records).forEach { (artifact, record) ->
                if (record.targetExisted) Files.copy(artifact.target, Paths.get(record.backup), StandardCopyOption.REPLACE_EXISTING)
            }
            transition(journalPath, journal, TransactionState.PUBLISHING)
            activationStarted = true
            records.forEach { record ->
                val target = Paths.get(record.target)
                target.parent?.let(Files::createDirectories)
                Files.copy(Paths.get(record.staged), target, StandardCopyOption.REPLACE_EXISTING)
            }
            transition(journalPath, journal, TransactionState.AWAITING_HEALTH)
            if (!healthCheck()) {
                rollback(journalPath, journal)
                return TransactionResult(journal.state, journalPath)
            }
            transition(journalPath, journal, TransactionState.COMMITTED)
            return TransactionResult(journal.state, journalPath)
        } catch (error: Throwable) {
            if (activationStarted) {
                runCatching { rollback(journalPath, journal) }.onFailure(error::addSuppressed)
            } else {
                runCatching { transition(journalPath, journal, TransactionState.FAILED) }.onFailure(error::addSuppressed)
            }
            throw error
        }
    }

    fun recover(journalPath: Path): TransactionResult {
        val journal = TransactionJournal.load(journalPath)
        if (journal.state in setOf(
                TransactionState.PUBLISHING,
                TransactionState.ACTIVATING,
                TransactionState.AWAITING_HEALTH,
                TransactionState.ROLLING_BACK,
            )
        ) {
            rollback(journalPath, journal)
        }
        return TransactionResult(journal.state, journalPath)
    }

    fun recoverAll(): List<TransactionResult> {
        if (!Files.isDirectory(root)) return emptyList()
        return Files.list(root).use { directories ->
            directories
                .filter(Files::isDirectory)
                .map { it.resolve("journal.json") }
                .filter(Files::isRegularFile)
                .sorted()
                .map(::recover)
                .toList()
        }
    }

    private fun rollback(path: Path, journal: TransactionJournal) {
        transition(path, journal, TransactionState.ROLLING_BACK)
        journal.artifacts.forEach { record ->
            val target = Paths.get(record.target)
            if (record.targetExisted) {
                Files.copy(Paths.get(record.backup), target, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.deleteIfExists(target)
            }
        }
        transition(path, journal, TransactionState.ROLLED_BACK)
    }

    private fun transition(path: Path, journal: TransactionJournal, state: TransactionState) {
        journal.state = state
        TransactionJournal.save(path, journal)
    }
}
