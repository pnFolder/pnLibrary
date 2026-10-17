package ru.privatenull.pnlibrary.core.updates

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.updates.ProductId
import ru.privatenull.pnlibrary.api.updates.UpdateConfigurationBackup
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class ConfigurationSnapshotsTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `snapshot keeps exact bytes and absence without changing the live files`() {
        val (store, data) = fixture()
        val file = data.resolve("config.yml")
        Files.writeString(file, "message: Привет\n")
        val plan = UUID.randomUUID()
        val id = store.capture(plan, mapOf(ProductId.of("example") to UpdateConfigurationBackup(data, listOf("config.yml", "missing.yml"))))!!
        Files.writeString(file, "new setting")
        val snapshot = store.read(id)
        assertEquals(plan.toString(), snapshot.planId)
        assertTrue(snapshot.files[0].existed)
        assertFalse(snapshot.files[1].existed)
        assertEquals("new setting", Files.readString(file))
        assertEquals("message: Привет\n", Files.readString(directory.resolve("snapshots/$id/${snapshot.files[0].blob}")))
        assertFalse(Files.exists(data.resolve("missing.yml")))
    }

    @Test
    fun `corrupt backup bytes and escaped manifest paths are rejected`() {
        val (store, data) = fixture()
        Files.writeString(data.resolve("config.yml"), "original")
        val id = store.capture(UUID.randomUUID(), mapOf(ProductId.of("example") to UpdateConfigurationBackup(data, listOf("config.yml"))))!!
        val snapshot = store.read(id)
        val blob = directory.resolve("snapshots/$id/${snapshot.files.single().blob}")
        Files.writeString(blob, "tampered")
        assertThrows(IllegalArgumentException::class.java) { store.read(id) }
        Files.writeString(blob, "original")
        val changed = snapshot.copy(files = listOf(snapshot.files.single().copy(relative = "../../outside.yml")))
        Files.writeString(directory.resolve("snapshots/$id/manifest.json"), Gson().toJson(changed))
        assertThrows(IllegalArgumentException::class.java) { store.read(id) }
        assertEquals("original", Files.readString(data.resolve("config.yml")))
    }

    @Test
    fun `directories oversized files and files outside plugins are not captured`() {
        val (_, data) = fixture()
        val store = ConfigurationSnapshots(directory.resolve("snapshots"), directory.resolve("plugins"), maximumFileBytes = 2)
        Files.writeString(data.resolve("large.yml"), "123")
        assertThrows(IllegalArgumentException::class.java) { store.capture(UUID.randomUUID(), mapOf(ProductId.of("example") to UpdateConfigurationBackup(data, listOf("large.yml")))) }
        Files.createDirectory(data.resolve("nested"))
        assertThrows(IllegalArgumentException::class.java) { store.capture(UUID.randomUUID(), mapOf(ProductId.of("example") to UpdateConfigurationBackup(data, listOf("nested")))) }
        assertThrows(IllegalArgumentException::class.java) { store.capture(UUID.randomUUID(), mapOf(ProductId.of("example") to UpdateConfigurationBackup(directory.resolve("outside"), listOf("config.yml")))) }
        assertThrows(IllegalArgumentException::class.java) { UpdateConfigurationBackup(data, listOf("../outside.yml")) }
    }

    private fun fixture(): Pair<ConfigurationSnapshots, Path> {
        val plugins = directory.resolve("plugins")
        val data = plugins.resolve("example").also(Files::createDirectories)
        return ConfigurationSnapshots(directory.resolve("snapshots"), plugins) to data
    }
}
