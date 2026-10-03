package ru.privatenull.pnlibrary.core.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderAccess
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderCachePolicy
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderCacheScope
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderKey
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderRequest
import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class PlaceholderEntryTest {
    @Test
    fun `entry owns resolution caching and lifecycle`() {
        val owner = PluginId.of("demo")
        val calls = AtomicInteger()
        val entry = PlaceholderEntry(
            owner = owner,
            key = PlaceholderKey.of("status", String::class.java),
            resolver = {
                CompletableFuture.completedFuture("online-${calls.incrementAndGet()}")
            },
            updater = null,
            access = PlaceholderAccess.ownerOnly(),
            updateAccess = PlaceholderAccess.ownerOnly(),
            cachePolicy = PlaceholderCachePolicy(PlaceholderCacheScope.GLOBAL, 1, 60_000),
            fallback = "offline",
            adapterLookup = { null },
        )
        val request = PlaceholderRequest(owner, owner, null, emptyMap(), emptyMap())

        assertEquals("online-1", entry.resolve(request).toCompletableFuture().join())
        assertEquals("online-1", entry.resolve(request).toCompletableFuture().join())
        assertEquals(1, calls.get())

        entry.disable()
        assertEquals("offline", entry.resolve(request).toCompletableFuture().join())

        entry.close()
        assertThrows(IllegalStateException::class.java) { entry.enable() }
    }

    @Test
    fun `entry remembers cached null values when policy allows them`() {
        val owner = PluginId.of("demo")
        val calls = AtomicInteger()
        val entry = PlaceholderEntry(
            owner = owner,
            key = PlaceholderKey.of("optional", String::class.java),
            resolver = {
                calls.incrementAndGet()
                CompletableFuture.completedFuture(null)
            },
            updater = null,
            access = PlaceholderAccess.ownerOnly(),
            updateAccess = PlaceholderAccess.ownerOnly(),
            cachePolicy = PlaceholderCachePolicy(
                scope = PlaceholderCacheScope.GLOBAL,
                maximumEntries = 1,
                expireAfterWriteMillis = 60_000,
                cacheNullValues = true,
            ),
            fallback = null,
            adapterLookup = { null },
        )
        val request = PlaceholderRequest(owner, owner, null, emptyMap(), emptyMap())

        entry.resolve(request).toCompletableFuture().join()
        entry.resolve(request).toCompletableFuture().join()

        assertEquals(1, calls.get())
    }
}
