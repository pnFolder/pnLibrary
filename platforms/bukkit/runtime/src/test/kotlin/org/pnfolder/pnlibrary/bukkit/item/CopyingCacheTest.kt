package org.pnfolder.pnlibrary.bukkit.item

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CopyingCacheTest {
    private class Stack(var name: String)

    private fun cache(capacity: Int) = CopyingCache<Stack>(capacity) { Stack(it.name) }

    @Test
    fun `callers get copies and cannot change what is stored`() {
        val cache = cache(4)
        val original = Stack("head")
        cache["a"] = original
        original.name = "changed after storing"

        val first = cache["a"]!!
        first.name = "changed after reading"

        assertNotSame(first, cache["a"])
        assertEquals("head", cache["a"]!!.name)
    }

    @Test
    fun `the cache never grows past its capacity and drops the least recently used`() {
        val cache = cache(3)
        listOf("a", "b", "c").forEach { cache[it] = Stack(it) }
        cache["a"]
        cache["d"] = Stack("d")

        assertEquals(3, cache.size)
        assertNull(cache["b"])
        assertEquals(setOf("a", "c", "d"), listOf("a", "c", "d").filter { cache[it] != null }.toSet())
        repeat(1000) { cache["k$it"] = Stack("k$it") }
        assertEquals(3, cache.size)
    }

    @Test
    fun `parallel use stays within capacity`() {
        val cache = cache(16)
        val pool = Executors.newFixedThreadPool(4)
        repeat(4) { thread -> pool.execute { repeat(2000) { cache["${thread}-${it % 50}"] = Stack("x"); cache["${thread}-${it % 50}"] } } }
        pool.shutdown()
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        assertTrue(cache.size <= 16)
    }
}
