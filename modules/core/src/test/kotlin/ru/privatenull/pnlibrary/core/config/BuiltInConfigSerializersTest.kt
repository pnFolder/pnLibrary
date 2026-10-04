package ru.privatenull.pnlibrary.core.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.config.ConfigSerializationContext
import java.time.Duration
import java.util.Locale

class BuiltInConfigSerializersTest {
    private val serializers = BuiltInConfigSerializers.create()

    @Test
    fun `duration serializer accepts compact units and iso notation`() {
        val serializer = serializer(Duration::class.java)

        assertEquals(Duration.ofMillis(250), serializer.deserialize("250ms", context(Duration::class.java)))
        assertEquals(Duration.ofMinutes(5), serializer.deserialize("5m", context(Duration::class.java)))
        assertEquals(Duration.ofHours(2), serializer.deserialize("PT2H", context(Duration::class.java)))
    }

    @Test
    fun `locale serializer uses language tags in both directions`() {
        val serializer = serializer(Locale::class.java)
        val locale = Locale.forLanguageTag("pt-BR")

        assertEquals("pt-BR", serializer.serialize(locale, context(Locale::class.java)))
        assertEquals(locale, serializer.deserialize("pt-BR", context(Locale::class.java)))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> serializer(type: Class<T>) =
        serializers.getValue(type) as ru.privatenull.pnlibrary.api.config.ConfigSerializer<T>

    private fun context(type: Class<*>) = ConfigSerializationContext(
        path = "value",
        declaredType = type,
        rawType = type,
        annotations = emptyList(),
        defaultValue = null,
    )
}
