package org.pnfolder.pnlibrary.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.config.ConfigOptions
import org.pnfolder.pnlibrary.api.config.ConfigRange
import org.pnfolder.pnlibrary.api.config.ConfigValidationException
import org.pnfolder.pnlibrary.api.config.ConfigValidatorBuilder
import org.pnfolder.pnlibrary.api.config.type
import org.pnfolder.pnlibrary.api.config.yaml
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership
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
    fun `loadAll loads the healthy files even when one file is broken`() {
        Files.writeString(directory.resolve("broken.yml"), "retries: [unclosed")
        service().use { service ->
            val scope = scope(service)
            val broken = scope.yaml<ServiceSettings>("broken.yml") { ServiceSettings() }
            val healthy = scope.yaml<ServiceSettings>("healthy.yml") { ServiceSettings() }

            assertThrows(RuntimeException::class.java) { scope.loadAll() }

            assertTrue(healthy.isLoaded)
            assertTrue(!broken.isLoaded)
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
    fun `every new file carries a version marker with a bilingual warning`() {
        service().use { service ->
            scope(service).yaml<ServiceSettings>("settings.yml") { ServiceSettings() }.load()
            val text = Files.readString(directory.resolve("settings.yml"))
            assertTrue(text.contains("_config-version: 1"), text)
            assertTrue(text.contains("DO NOT EDIT OR REMOVE"), text)
            assertTrue(text.contains("НЕ МЕНЯЙТЕ И НЕ УДАЛЯЙТЕ"), text)
        }
    }

    @Test
    fun `every file carries the support note once, new or already existing`() {
        Files.writeString(directory.resolve("old.yml"), "retries: 4"+"\n"+"name: custom"+"\n")
        service().use { service ->
            val scope = scope(service)
            scope.yaml<ServiceSettings>("settings.yml") { ServiceSettings() }.load()
            val old = scope.yaml<ServiceSettings>("old.yml") { ServiceSettings() }
            old.load()
            old.reload()
            listOf("settings.yml", "old.yml").forEach { name ->
                val text = Files.readString(directory.resolve(name))
                assertEquals(1, text.split("discord.gg").size - 1, "$name: $text")
                assertTrue(text.contains("write to our Discord"), text)
                assertTrue(text.contains("пишите в наш Discord"), text)
            }
        }
    }

    @Test
    fun `administrator comments survive reload and a missing marker is added`() {
        Files.writeString(directory.resolve("settings.yml"), "# my note\nretries: 4\nname: custom\n")
        service().use { service ->
            val config = scope(service).yaml<ServiceSettings>("settings.yml") { ServiceSettings() }
            config.load()
            config.reload()
            val text = Files.readString(directory.resolve("settings.yml"))
            assertTrue(text.contains("# my note"), text)
            assertTrue(text.contains("_config-version: 1"), text)
            assertEquals(4, config.value.retries)
        }
    }

    @Test
    fun `withoutVersion omits the marker`() {
        service().use { service ->
            val options = ConfigOptions.builder().withoutVersion().build()
            scope(service).yaml("settings.yml", options) { ServiceSettings() }.load()
            assertFalse(Files.readString(directory.resolve("settings.yml")).contains("_config-version"))
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
