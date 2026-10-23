package org.pnfolder.pnlibrary.core.updates.history

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.updates.ProductId
import org.pnfolder.pnlibrary.api.updates.backup.UpdateConfigurationBackup
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class ConfigurationRestorerTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `restoration preserves current files first and only changes declared paths`() {
        val (snapshots, data) = fixture()
        val config = data.resolve("config.yml")
        Files.writeString(config, "version: 1")
        val original = snapshots.capture(UUID.randomUUID(), mapOf(ProductId.of("example") to UpdateConfigurationBackup(data, listOf("config.yml",
            "optional.yml"))))!!
        Files.writeString(config, "version: 2")
        Files.writeString(data.resolve("optional.yml"), "new generated file")
        Files.writeString(data.resolve("unrelated.yml"), "keep me")
        val restorer = ConfigurationRestorer(snapshots, directory.resolve("restores"))
        val result = restorer.restore(original)
        assertEquals("version: 1", Files.readString(config))
        assertFalse(Files.exists(data.resolve("optional.yml")))
        assertEquals("keep me", Files.readString(data.resolve("unrelated.yml")))
        val journal = journal(result)
        assertEquals("APPLIED", journal.state)
        val before = snapshots.read(UUID.fromString(journal.before))
        assertTrue(before.files.all { it.existed })
        restorer.restore(UUID.fromString(journal.before))
        assertEquals("version: 2", Files.readString(config))
        assertEquals("new generated file", Files.readString(data.resolve("optional.yml")))
    }

    @Test
    fun `interrupted restore recovers the saved pre restore state`() {
        val (snapshots, data) = fixture()
        Files.writeString(data.resolve("config.yml"), "old")
        val original = snapshots.capture(UUID.randomUUID(), mapOf(ProductId.of("example") to UpdateConfigurationBackup(data, listOf("config.yml"))))!!
        Files.writeString(data.resolve("config.yml"), "current")
        val root = directory.resolve("restores")
        val restorer = ConfigurationRestorer(snapshots, root)
        val result = restorer.restore(original)
        val journal = journal(result).copy(state = "APPLYING")
        Files.writeString(root.resolve("$result/restore.json"), Gson().toJson(journal))
        ConfigurationRestorer(snapshots, root).recoverInterrupted()
        assertEquals("current", Files.readString(data.resolve("config.yml")))
        assertEquals("RECOVERED", journal(result).state)
    }

    @Test
    fun `damaged source snapshot cannot modify any live file`() {
        val (snapshots, data) = fixture()
        Files.writeString(data.resolve("config.yml"), "old")
        val original = snapshots.capture(UUID.randomUUID(), mapOf(ProductId.of("example") to UpdateConfigurationBackup(data, listOf("config.yml"))))!!
        Files.writeString(data.resolve("config.yml"), "current")
        val record = snapshots.read(original).files.single()
        Files.writeString(directory.resolve("snapshots/$original/${record.blob}"), "bad")
        assertThrows(IllegalArgumentException::class.java) {
            ConfigurationRestorer(snapshots, directory.resolve("restores")).restore(original)
        }
        assertEquals("current", Files.readString(data.resolve("config.yml")))
    }

    @Test
    fun `completed operation is not repeated and can be undone by its enclosing transaction`() {
        val (snapshots, data) = fixture()
        Files.writeString(data.resolve("config.yml"), "old")
        val original = snapshots.capture(UUID.randomUUID(), mapOf(ProductId.of("example") to UpdateConfigurationBackup(data, listOf("config.yml"))))!!
        Files.writeString(data.resolve("config.yml"), "current")
        val restorer = ConfigurationRestorer(snapshots, directory.resolve("restores"))
        val operation = UUID.randomUUID()
        val restored = restorer.restore(original, operation)
        assertEquals(restored, restorer.restore(original, operation))
        assertEquals("old", Files.readString(data.resolve("config.yml")))
        restorer.undo(restored)
        assertEquals("current", Files.readString(data.resolve("config.yml")))
        assertEquals("RECOVERED", journal(restored).state)
    }

    private fun journal(id: UUID) = Gson().fromJson(Files.readString(directory.resolve("restores/$id/restore.json")), ConfigurationRestorer.Journal::class.java)

    private fun fixture(): Pair<ConfigurationSnapshots, Path> {
        val plugins = directory.resolve("plugins")
        val data = plugins.resolve("example").also(Files::createDirectories)
        return ConfigurationSnapshots(directory.resolve("snapshots"), plugins) to data
    }
}
