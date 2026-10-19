package ru.privatenull.pnlibrary.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.config.ConfigOptions
import ru.privatenull.pnlibrary.api.config.ConfigRange
import ru.privatenull.pnlibrary.api.config.ConfigValidationException
import ru.privatenull.pnlibrary.api.config.ConfigValidatorBuilder
import ru.privatenull.pnlibrary.api.config.type
import ru.privatenull.pnlibrary.api.config.yaml
import ru.privatenull.pnlibrary.api.plugin.PluginId
import ru.privatenull.pnlibrary.spi.platform.PlatformOwnership
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger

/** Plugin-like owner exposing the Bukkit-style accessors the service looks for. */
class FakeConfigOwner(private val folder: File) {
    fun getDataFolder(): File = folder
    fun getLogger(): Logger = Logger.getLogger("fake-owner")
}

data class ServiceSettings(
    @ConfigRange(min = 1.0, max = 10.0)
    var retries: Int = 3,
    var name: String = "demo",
)

class ConfigurationServiceImplTest {
    @TempDir lateinit var directory: Path

    private fun service() = ConfigurationServiceImpl(object : PlatformOwnership {})

    private fun scope(service: ConfigurationServiceImpl) =
        service.scope(FakeConfigOwner(directory.toFile()), PluginId.of("tester"))

    @Test
    fun `creates the file from defaults and loads the value`() {
        service().use { service ->
            val config = scope(service).yaml<ServiceSettings>("settings.yml") { ServiceSettings() }
            val loaded = config.load().value
            assertEquals(3, loaded.retries)
            assertTrue(Files.exists(directory.resolve("settings.yml")))
        }
    }

    @Test
    fun `annotation and builder validators report together`() {
        Files.writeString(directory.resolve("settings.yml"), "retries: 99\nname: \"\"\n")
        val validator = ConfigValidatorBuilder<ServiceSettings>()
            .require("name", "must not be blank") { it.name.isNotBlank() }
            .build()
        service().use { service ->
            val config = scope(service).yaml("settings.yml", ConfigOptions.DEFAULT, validator) { ServiceSettings() }
            val error = assertThrows(ConfigValidationException::class.java) { config.load() }
            val paths = error.problems.map { it.path }
            assertTrue("retries" in paths, "annotation problem expected, was $paths")
            assertTrue("name" in paths, "builder problem expected, was $paths")
        }
    }

    @Test
    fun `builder validator passes when the value is valid`() {
        val validator = ConfigValidatorBuilder<ServiceSettings>()
            .require("name", "must not be blank") { it.name.isNotBlank() }
            .build()
        service().use { service ->
            val config = scope(service).yaml("settings.yml", ConfigOptions.DEFAULT, validator) { ServiceSettings() }
            assertEquals("demo", config.load().value.name)
        }
    }

    @Test
    fun `type builder publishes a named implementation and closes it`() {
        service().use { service ->
            val registration = scope(service).type<Shape, Circle>().name("Circle").alias("round").priority(5).register()
            assertEquals("circle", registration.name)
            assertEquals(PluginId.of("tester"), registration.owner)
            assertEquals(Circle::class.java, registration.implementation)
            assertTrue(registration.isActive)
            registration.close()
            assertFalse(registration.isActive)
        }
    }

    @Test
    fun `type builder requires a name before registering`() {
        service().use { service ->
            assertThrows(IllegalArgumentException::class.java) { scope(service).type<Shape, Circle>().register() }
        }
    }
}

interface Shape
class Circle : Shape
