package ru.privatenull.pnlibrary.core.platform

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PlatformProviderImplTest {
    private interface TestPlatform
    private object Implementation : TestPlatform

    @Test
    fun `registers and removes a typed platform implementation`() {
        val provider = PlatformProviderImpl()

        assertNull(provider.get(TestPlatform::class.java))
        val registration = provider.register(TestPlatform::class.java, Implementation)
        assertSame(Implementation, provider.require(TestPlatform::class.java))

        registration.close()
        assertNull(provider.get(TestPlatform::class.java))
    }

    @Test
    fun `rejects duplicate registration and missing required platform`() {
        val provider = PlatformProviderImpl()
        provider.register(TestPlatform::class.java, Implementation)

        assertThrows(IllegalArgumentException::class.java) {
            provider.register(TestPlatform::class.java, Implementation)
        }
        val missing = assertThrows(IllegalStateException::class.java) {
            provider.require(Runnable::class.java)
        }
        assert(missing.message.orEmpty().contains(Runnable::class.java.name))
    }
}
