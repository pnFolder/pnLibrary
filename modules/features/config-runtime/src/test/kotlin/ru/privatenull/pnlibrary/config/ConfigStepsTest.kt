package ru.privatenull.pnlibrary.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.config.ConfigOptions
import ru.privatenull.pnlibrary.api.config.ConfigSteps
import ru.privatenull.pnlibrary.api.config.ManagedConfig
import ru.privatenull.pnlibrary.api.config.yaml
import ru.privatenull.pnlibrary.api.plugin.PluginId
import ru.privatenull.pnlibrary.spi.platform.PlatformOwnership
import java.nio.file.Files
import java.nio.file.Path

data class StepSettings(
    var host: String = "",
    var port: Int = 0,
    var pool: Int = 10,
    var first: String = "a",
    var second: String = "b",
)

class StepsForTest : ConfigSteps<StepSettings>() {
    init {
        version(2) {
            rename("pool-size", "pool")
        }
        version(3) {
            val address = get("address")
            put("host", address.before(":"))
            put("port", address.after(":").toInt())
            remove("address")
        }
        version(4) {
            val first = get("first")
            val second = get("second")
            put("first", second)
            put("second", first)
        }
    }
}

class ConfigStepsTest {
    @TempDir lateinit var directory: Path

    private fun open(steps: ConfigSteps<*>): ManagedConfig<StepSettings> {
        val service = ConfigurationServiceImpl(object : PlatformOwnership {})
        val scope = service.scope(FakeConfigOwner(directory.toFile()), PluginId.of("steps"))
        val options = ConfigOptions.builder().migrations(steps).build()
        return scope.yaml("settings.yml", options) { StepSettings() }
    }

    private fun write(text: String) = Files.writeString(directory.resolve("settings.yml"), text)

    private fun read() = Files.readString(directory.resolve("settings.yml"))

    @Test
    fun `a file without a marker runs every version above one`() {
        write("pool-size: 20\naddress: \"db.local:3306\"\nfirst: x\nsecond: y\n")

        val loaded = open(StepsForTest()).load().value

        assertEquals("db.local", loaded.host)
        assertEquals(3306, loaded.port)
        assertEquals(20, loaded.pool)
        assertEquals("y", loaded.first)
        assertEquals("x", loaded.second)
        assertTrue(read().contains("_config-version: 4"), read())
        assertFalse(read().contains("pool-size"))
        assertFalse(read().contains("address"))
    }

    @Test
    fun `reloading a file at the latest version changes nothing`() {
        write("_config-version: 4\nhost: h\nport: 1\npool: 5\nfirst: a\nsecond: b\n")
        val config = open(StepsForTest())

        config.load()
        val afterFirstLoad = read()
        config.reload()

        assertEquals(afterFirstLoad, read())
        assertEquals("h", config.value.host)
    }

    @Test
    fun `a swap of two values stores each one under the other key`() {
        write("_config-version: 3\nhost: h\nport: 1\npool: 5\nfirst: left\nsecond: right\n")

        val loaded = open(StepsForTest()).load().value

        assertEquals("right", loaded.first)
        assertEquals("left", loaded.second)
    }

    @Test
    fun `a file from a newer version is read but never rewritten`() {
        val text = "_config-version: 9\nhost: future\nport: 2\npool: 3\nfirst: a\nsecond: b\n"
        write(text)

        val loaded = open(StepsForTest()).load().value

        assertEquals("future", loaded.host)
        assertEquals(text, read())
    }

    @Test
    fun `a failing step leaves the original file untouched`() {
        val text = "address: \"no-port-here\"\nfirst: a\nsecond: b\n"
        write(text)

        assertThrows(IllegalStateException::class.java) { open(StepsForTest()).load() }
        assertEquals(text, read())
    }

    @Test
    fun `version numbers must be consecutive`() {
        class Gapped : ConfigSteps<StepSettings>() {
            init {
                version(2) { rename("pool-size", "pool") }
                version(4) { remove("second") }
            }
        }

        assertThrows(IllegalArgumentException::class.java) { Gapped().applyTo(emptyDocument(), 1) }
    }

    @Test
    fun `latest version is the highest declared number, or one with no steps`() {
        assertEquals(4, StepsForTest().latestVersion)
        assertEquals(ConfigSteps.BASE_VERSION, ConfigSteps.NONE.latestVersion)
    }

    private fun emptyDocument() = ru.privatenull.pnlibrary.api.config.ConfigDocument(emptyMap())
}
