package ru.privatenull.pnlibrary.spi.platform

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PlatformLifecycleTest {
    @Test
    fun `binding is single shot until close`() {
        val lifecycle = PlatformLifecycle()

        lifecycle.beginBinding()
        assertThrows<IllegalStateException> { lifecycle.beginBinding() }
        lifecycle.endBinding()
        lifecycle.beginBinding()
        assertTrue(lifecycle.close())
        assertFalse(lifecycle.close())
        assertThrows<IllegalStateException> { lifecycle.checkOpen() }
    }

    @Test
    fun `failed binding rolls back bound state`() {
        val lifecycle = PlatformLifecycle()

        assertThrows<IllegalArgumentException> {
            lifecycle.bind {
                throw IllegalArgumentException("registration failed")
            }
        }

        lifecycle.bind { "registered" }
        assertThrows<IllegalStateException> { lifecycle.beginBinding() }
    }
}
