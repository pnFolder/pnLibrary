package ru.privatenull.pnlibrary.bukkit.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.logging.Logger

class CodeFirstYamlTest {
    @TempDir lateinit var directory: Path
    @Test fun `adds nested defaults with comments and preserves administrator values`() {
        val current = """
            # Existing comment
            database:
              host: custom.example
            addon:
              own-key: true
        """.trimIndent() + "\n"
        val defaults = """
            # Database settings
            database:
              # Server port
              port: 3306
              host: localhost
            # New feature
            feature:
              enabled: true
        """.trimIndent() + "\n"

        val result = YamlDefaultsMerger.merge(current, defaults)
        assertTrue(result.changed)
        assertTrue("database.port" in result.addedPaths)
        assertTrue("feature" in result.addedPaths)
        assertTrue(result.content.contains("host: custom.example"))
        assertTrue(result.content.contains("own-key: true"))
        assertTrue(result.content.contains("# Server port\n  port: 3306"))
    }

    @Test fun `second synchronization is idempotent`() {
        val current = "root:\n  existing: 5\n"
        val defaults = "root:\n  existing: 1\n  added: 2\n"
        val first = YamlDefaultsMerger.merge(current, defaults)
        val second = YamlDefaultsMerger.merge(first.content, defaults)
        assertFalse(second.changed)
        assertEquals(first.content, second.content)
    }

    @Test fun `managed lifecycle keeps last value after failed reload`() {
        data class Value(val port: Int)
        val file = directory.resolve("config.yml").toFile()
        val codec = object : ConfigCodec<Value> {
            override fun encode(value: Value) = "port: ${value.port}\n"
            override fun decode(yaml: String) = Value(Regex("port: (\\d+)").find(yaml)?.groupValues?.get(1)?.toInt()
                ?: error("port is missing"))
        }
        val managed = CodeFirstYaml(file, Value(3306), codec, Logger.getAnonymousLogger(),
            ConfigValidatorBuilder<Value>().require("port", "invalid") { it.port in 1..65535 }.build())

        assertEquals(3306, managed.loadValue().port)
        managed.update { it.copy(port = 5432) }
        assertEquals(5432, managed.value.port)
        file.writeText("port: 99999\n")
        assertThrows(ConfigValidationException::class.java) { managed.reload() }
        assertEquals(5432, managed.value.port)
        managed.unload()
        assertFalse(managed.isLoaded)
    }
}
