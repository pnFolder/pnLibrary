package ru.privatenull.pnlibrary.core.config.yaml

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.config.ConfigNamingStrategy
import ru.privatenull.pnlibrary.api.config.ConfigPolymorphic
import ru.privatenull.pnlibrary.api.config.ConfigType
import ru.privatenull.pnlibrary.api.config.ConfigTypes

class ConfigPolymorphicResolverTest {
    private val introspector = ConfigObjectIntrospector(ConfigNamingStrategy.AS_DECLARED)

    @Test
    fun `resolves built-in aliases case-insensitively`() {
        val resolver = resolver(emptyList(), consumer = "shop")

        assertTrue(resolver.supports(Payment::class.java, emptyList()))
        assertEquals(
            CardPayment::class.java,
            resolver.decodingType(Payment::class.java, emptyList(), "LEGACY-CARD", "payment"),
        )
    }

    @Test
    fun `external runtime type requires owner namespace`() {
        val external = RuntimeConfigType(
            owner = "economy",
            baseType = Payment::class.java,
            implementation = TokenPayment::class.java,
            name = "token",
            aliases = emptySet(),
            priority = 0,
        )
        val resolver = resolver(listOf(external), consumer = "shop")

        assertThrows(IllegalArgumentException::class.java) {
            resolver.decodingType(Payment::class.java, emptyList(), "token", "payment")
        }
        assertEquals(
            TokenPayment::class.java,
            resolver.decodingType(Payment::class.java, emptyList(), "economy::token", "payment"),
        )
        assertEquals(
            "economy::token",
            resolver.encodingType(Payment::class.java, emptyList(), TokenPayment::class.java)
                .serializedName("shop"),
        )
    }

    @Test
    fun `consumer-owned runtime type can use an unqualified name`() {
        val owned = RuntimeConfigType(
            owner = "shop",
            baseType = Payment::class.java,
            implementation = TokenPayment::class.java,
            name = "token",
            aliases = setOf("coins"),
            priority = 10,
        )
        val resolver = resolver(listOf(owned), consumer = "SHOP")

        assertEquals(
            TokenPayment::class.java,
            resolver.decodingType(Payment::class.java, emptyList(), "coins", "payment"),
        )
    }

    private fun resolver(types: List<RuntimeConfigType>, consumer: String) =
        ConfigPolymorphicResolver({ types }, consumer, introspector)

    @ConfigPolymorphic
    @ConfigTypes(
        ConfigType(
            type = CardPayment::class,
            name = "card",
            aliases = ["legacy-card"],
        ),
    )
    private interface Payment

    private class CardPayment : Payment
    private class TokenPayment : Payment
}
