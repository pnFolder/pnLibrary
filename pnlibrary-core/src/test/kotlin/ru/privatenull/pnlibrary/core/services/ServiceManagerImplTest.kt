package ru.privatenull.pnlibrary.core.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.plugin.PluginId

class ServiceManagerImplTest {
    @Test
    fun `highest numeric priority wins and all providers remain available`() {
        ServiceManagerImpl().use { services ->
            services.register(PluginId.of("fallback"), GreetingService::class.java, Greeting("fallback"), -100)
            val preferredOwner = PluginId.of("preferred")
            services.register(
                preferredOwner,
                GreetingService::class.java,
                Greeting("preferred"),
                500,
            )

            assertEquals("preferred", services.require(GreetingService::class.java).value)
            assertEquals(listOf("preferred", "fallback"), services.getAll(GreetingService::class.java).map { it.value })

            services.unregister(preferredOwner, GreetingService::class.java)
            assertEquals("fallback", services.require(GreetingService::class.java).value)
        }
    }

    @Test
    fun `all services owned by a plugin are removed together`() {
        ServiceManagerImpl().use { services ->
            val owner = PluginId.of("example")
            services.register(owner, GreetingService::class.java, Greeting("hello"))

            assertEquals("hello", services.get(GreetingService::class.java)?.value)
            services.unregisterAll(owner)

            assertNull(services.get(GreetingService::class.java))
        }
    }

    @Test
    fun `one owner cannot accidentally publish the same contract twice`() {
        ServiceManagerImpl().use { services ->
            val owner = PluginId.of("example")
            services.register(owner, GreetingService::class.java, Greeting("first"))

            assertThrows(IllegalStateException::class.java) {
                services.register(owner, GreetingService::class.java, Greeting("duplicate"))
            }
        }
    }

    private interface GreetingService {
        val value: String
    }

    private data class Greeting(override val value: String) : GreetingService
}
