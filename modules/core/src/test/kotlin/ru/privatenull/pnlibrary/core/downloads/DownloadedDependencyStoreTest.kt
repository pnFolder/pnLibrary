package ru.privatenull.pnlibrary.core.downloads

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DownloadedDependencyStoreTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `records dependencies and consumes those found after restart`() {
        val store = DownloadedDependencyStore(directory)

        store.record(listOf("Vault", "LuckPerms", "vault"))

        assertEquals(listOf("LuckPerms", "Vault"), Files.readAllLines(store.marker))
        assertEquals(
            listOf(DownloadedDependency("vault", "1.7.3")),
            store.consumeInstalled(mapOf("vault" to "1.7.3")),
        )
        assertEquals(listOf("LuckPerms"), Files.readAllLines(store.marker))

        assertEquals(
            listOf(DownloadedDependency("LuckPerms", "5.4.0")),
            store.consumeInstalled(mapOf("LuckPerms" to "5.4.0")),
        )
        assertFalse(Files.exists(store.marker))
    }
}
