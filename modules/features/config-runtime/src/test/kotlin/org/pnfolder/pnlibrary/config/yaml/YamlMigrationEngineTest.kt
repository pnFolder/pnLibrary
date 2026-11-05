package org.pnfolder.pnlibrary.config.yaml

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.config.ConfigSteps
import java.nio.file.Files
import java.nio.file.Path

class YamlMigrationEngineTest {
    @TempDir lateinit var directory: Path

    private val engine = YamlMigrationEngine()

    private class Steps : ConfigSteps<Any>() {
        init {
            version(2) { rename("old", "new") }
            version(3) {
                put("db.host", get("host"))
                remove("host")
            }
            version(4) { remove("legacy") }
        }
    }

    @Test
    fun `renames a key and moves a value into a nested section`() {
        val result = engine.migrate("old: 1\nhost: h\nlegacy: x\nkeep: k\n", Steps())

        assertEquals(listOf("v2", "v3", "v4"), result.applied)
        assertTrue(result.content.contains("new: 1"), result.content)
        assertTrue(result.content.contains("db:\n  host: h"), result.content)
        assertTrue(result.content.contains("keep: k"))
        assertFalse(result.content.contains("old:"))
        assertFalse(result.content.contains("legacy"))
    }

    @Test
    fun `the result carries the latest version marker once`() {
        val result = engine.migrate("old: 1\nhost: h\n", Steps())

        assertEquals(1, Regex("(?m)^_config-version: ").findAll(result.content).count(), result.content)
        assertTrue(result.content.contains("_config-version: 4\n"))
    }

    @Test
    fun `only versions above the stored one are applied`() {
        val result = engine.migrate("_config-version: 3\nlegacy: x\nold: stays\n", Steps())

        assertEquals(listOf("v4"), result.applied)
        assertTrue(result.content.contains("old: stays"), result.content)
        assertFalse(result.content.contains("legacy"))
    }

    @Test
    fun `a file at the latest version is not changed in meaning`() {
        val result = engine.migrate("_config-version: 4\nnew: 1\n", Steps())

        assertTrue(result.applied.isEmpty())
        assertFalse(result.newerThanCode)
        assertTrue(result.content.contains("new: 1"))
    }

    @Test
    fun `a newer file is returned as is and flagged`() {
        val text = "_config-version: 9\nold: 1\n"

        val result = engine.migrate(text, Steps())

        assertTrue(result.newerThanCode)
        assertEquals(text, result.content)
        assertTrue(result.applied.isEmpty())
    }

    @Test
    fun `an empty file gets only the marker`() {
        val result = engine.migrate("", ConfigSteps.NONE)

        assertTrue(result.content.endsWith("_config-version: ${ConfigSteps.NONE.latestVersion}\n"), result.content)
        assertTrue(result.applied.isEmpty())
    }

    @Test
    fun `a root that is not an object is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { engine.migrate("- a\n- b\n", Steps()) }
    }

    @Test
    fun `a failing step throws and the file on disk stays as it was`() {
        class Failing : ConfigSteps<Any>() {
            init {
                version(2) { put("n", get("count").toInt()) }
            }
        }
        val file = directory.resolve("c.yml")
        val text = "count: not-a-number\n"
        Files.writeString(file, text)

        assertThrows(IllegalStateException::class.java) { engine.migrate(Files.readString(file), Failing()) }
        assertEquals(text, Files.readString(file))
    }
}
