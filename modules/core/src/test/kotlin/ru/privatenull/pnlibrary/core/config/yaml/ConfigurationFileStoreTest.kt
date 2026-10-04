package ru.privatenull.pnlibrary.core.config.yaml

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ConfigurationFileStoreTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `atomic write normalizes line endings and validates before replacement`() {
        val file = directory.resolve("config.yml").toFile()
        file.writeText("value: old\n")
        val store = ConfigurationFileStore(file, validator = { content ->
            require("invalid" !in content)
        })

        store.write("value: new\r\n")
        assertEquals("value: new\n", file.readText())

        runCatching { store.write("invalid\n") }
        assertEquals("value: new\n", file.readText())
        assertFalse(directory.toFile().listFiles().orEmpty().any { it.extension == "tmp" })
    }

    @Test
    fun `backup retention keeps only the five newest files`() {
        val file = directory.resolve("config.yml").toFile()
        val store = ConfigurationFileStore(file, validator = {})

        repeat(7) { index ->
            store.backup("revision-$index")
            Thread.sleep(2)
        }

        val backups = Files.list(directory).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".bak") }.toList()
        }
        assertEquals(5, backups.size)
        assertTrue(backups.all { Files.readString(it).startsWith("revision-") })
    }
}
