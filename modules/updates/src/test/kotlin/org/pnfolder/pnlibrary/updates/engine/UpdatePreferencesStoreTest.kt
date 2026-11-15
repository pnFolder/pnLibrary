package org.pnfolder.pnlibrary.updates.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.updates.ProductId
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.updates.policy.UpdatePolicyOverrides
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class UpdatePreferencesStoreTest {
    @TempDir
    lateinit var directory: Path

    private val file get() = directory.resolve("updates/preferences.json")
    private val clock = Clock.fixed(Instant.parse("2026-09-17T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `everything is saved in one file and read back`() {
        val store = UpdatePreferencesStore(file)
        store.update { it.copy(channels = mapOf("shop" to "BETA"), automatic = mapOf("shop" to true), allPlugins = false, freezes = mapOf("shop" to 5L)) }

        val reopened = UpdatePreferencesStore(file).current()
        assertEquals(mapOf("shop" to "BETA"), reopened.channels)
        assertEquals(mapOf("shop" to true), reopened.automatic)
        assertEquals(false, reopened.allPlugins)
        assertEquals(mapOf("shop" to 5L), reopened.freezes)
    }

    @Test
    fun `the choices and the pauses keep each others sections`() {
        val store = UpdatePreferencesStore(file)
        val overrides = UpdatePolicyOverrides(store)
        val freezes = FreezeStore(store, clock)
        val shop = ProductId.of("shop")

        freezes.freeze(shop, Duration.ofHours(2))
        overrides.setChannel("shop", UpdateChannel.RC)
        overrides.setAutomatic("shop", true)
        freezes.freeze(ProductId.of("auth"), Duration.ofHours(1))

        val reopened = UpdatePreferencesStore(file)
        assertEquals(UpdateChannel.RC, UpdatePolicyOverrides(reopened).channel("shop"))
        assertEquals(true, UpdatePolicyOverrides(reopened).automatic("shop"))
        assertEquals(setOf(shop, ProductId.of("auth")), FreezeStore(reopened, clock).active().keys)
    }

    @Test
    fun `concurrent saves from both owners are all kept`() {
        val store = UpdatePreferencesStore(file)
        val overrides = UpdatePolicyOverrides(store)
        val freezes = FreezeStore(store, clock)
        val workers = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val rounds = 40
        try {
            val first = workers.submit { start.await(); repeat(rounds) { overrides.setAutomatic("p$it", true) } }
            val second = workers.submit { start.await(); repeat(rounds) { freezes.freeze(ProductId.of("c$it"), Duration.ofHours(1)) } }
            start.countDown()
            first.get(30, TimeUnit.SECONDS)
            second.get(30, TimeUnit.SECONDS)
        } finally {
            workers.shutdownNow()
        }

        val reopened = UpdatePreferencesStore(file)
        assertEquals(rounds, reopened.current().automatic.size)
        assertEquals(rounds, FreezeStore(reopened, clock).active().size)
    }

    @Test
    fun `an unreadable file is reported and replaced by the next save`() {
        Files.createDirectories(file.parent)
        Files.writeString(file, "{ not json")
        val warnings = mutableListOf<String>()
        val store = UpdatePreferencesStore(file, warnings::add)

        assertEquals(UpdatePreferencesStore.Preferences(), store.current())
        assertEquals(1, warnings.size)
        store.update { it.copy(allPlugins = true) }
        assertEquals(true, UpdatePreferencesStore(file).current().allPlugins)
    }

    @Test
    fun `nothing is written until something changes`() {
        val store = UpdatePreferencesStore(file)
        store.update { it }
        assertFalse(Files.exists(file))
        store.update { it.copy(allPlugins = true) }
        assertTrue(Files.exists(file))
    }
}
