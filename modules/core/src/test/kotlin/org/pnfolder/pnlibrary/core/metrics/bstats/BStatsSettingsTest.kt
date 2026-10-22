package org.pnfolder.pnlibrary.core.metrics.bstats

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class BStatsSettingsTest {
    @TempDir
    lateinit var directory: Path

    @BeforeEach
    fun skipRelocationCheck() {
        System.setProperty("bstats.relocatecheck", "false")
    }

    @AfterEach
    fun restoreRelocationCheck() {
        System.clearProperty("bstats.relocatecheck")
    }

    private fun file(name: String) = directory.resolve("bStats").resolve(name).toFile()

    @Test
    fun `a missing yaml file is created with an identifier and sending enabled`() {
        val file = file("config.yml")

        val first = BStatsSettings.yaml(file)
        val second = BStatsSettings.yaml(file)

        assertTrue(first.enabled)
        assertTrue(first.createdNow)
        assertFalse(second.createdNow)
        assertEquals(first.serverUuid, second.serverUuid)
        assertTrue(file.readText().contains("serverUuid: \"${first.serverUuid}\""))
    }

    @Test
    fun `the opt-out and logging flags of a file written by another plugin are kept`() {
        val file = file("config.yml")
        file.parentFile.mkdirs()
        file.writeText(
            """
            # bStats (https://bStats.org) collects some basic information for plugin authors
            enabled: false
            serverUuid: 0b1b8f2e-5c1e-4e4e-9d2a-1f6f7a1a0001
            logFailedRequests: true
            logSentData: 'true'
            logResponseStatusText: false
            """.trimIndent(),
        )

        val settings = BStatsSettings.yaml(file)

        assertFalse(settings.enabled)
        assertEquals("0b1b8f2e-5c1e-4e4e-9d2a-1f6f7a1a0001", settings.serverUuid)
        assertTrue(settings.logFailedRequests)
        assertTrue(settings.logSentData)
        assertFalse(settings.logResponseStatusText)
        assertFalse(settings.createdNow)
    }

    @Test
    fun `a file without an identifier gets one and keeps the opt-out`() {
        val file = file("config.yml")
        file.parentFile.mkdirs()
        file.writeText("enabled: false\n")

        val settings = BStatsSettings.yaml(file)

        assertFalse(settings.enabled)
        assertTrue(settings.serverUuid.isNotBlank())
        assertTrue(file.readText().contains("serverUuid: \"${settings.serverUuid}\""))
        assertTrue(file.readText().contains("enabled: false"))
    }

    @Test
    fun `the properties file of Velocity is created and read again`() {
        val file = file("config.txt")

        val first = BStatsSettings.properties(file)
        val second = BStatsSettings.properties(file)

        assertTrue(first.enabled)
        assertTrue(first.createdNow)
        assertFalse(second.createdNow)
        assertEquals(first.serverUuid, second.serverUuid)
        assertTrue(file.readText().contains("server-uuid=${first.serverUuid}"))
    }

    @Test
    fun `disabled settings send nothing and have their own identifier`() {
        val one = BStatsSettings.disabled()

        assertFalse(one.enabled)
        assertNotEquals(one.serverUuid, BStatsSettings.disabled().serverUuid)
    }

    @Test
    fun `the client starts and stops without sending while sending is off`() {
        val client = BStatsClient(
            platform = "bukkit",
            serviceId = 34864,
            settings = BStatsSettings.disabled(),
            pluginVersion = "1.0",
            appendPlatformData = {},
            warn = { _, _ -> },
            info = {},
        )

        client.addCustomChart(org.bstats.charts.SimplePie("rank") { "vip" })
        client.shutdown()
    }

    @Test
    fun `a file that cannot be read counts as an opt-out`() {
        val unreadable: File = directory.resolve("config.yml").toFile().also { it.mkdirs() }

        assertFalse(BStatsSettings.yaml(unreadable).enabled)
    }
}
