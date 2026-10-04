package ru.privatenull.pnlibrary.core.config.yaml

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.config.ConfigSerializationContext
import ru.privatenull.pnlibrary.api.config.ConfigSerializeWith
import ru.privatenull.pnlibrary.api.config.ConfigSerializer

class ConfigSerializerResolverTest {
    @Test
    fun `annotation serializer takes precedence and is reused`() {
        CountingSerializer.instances = 0
        val registered = TextSerializer("registered")
        val resolver = ConfigSerializerResolver(mapOf(AnnotatedValue::class.java to registered))

        val first = resolver.forType(AnnotatedValue::class.java)
        val second = resolver.forType(AnnotatedValue::class.java)

        assertEquals(1, CountingSerializer.instances)
        assertSame(first, second)
        assertEquals("annotation", first?.serialize(AnnotatedValue("value"), context()))
    }

    @Test
    fun `field annotation overrides the serializer selected for its type`() {
        val registered = TextSerializer("registered")
        val resolver = ConfigSerializerResolver(mapOf(AnnotatedValue::class.java to registered))
        val field = FieldOwner::class.java.getDeclaredField("value")

        val selected = resolver.forField(field)

        assertEquals("field", selected?.serialize(AnnotatedValue("value"), context()))
    }

    private fun context() = ConfigSerializationContext(
        path = "value",
        declaredType = AnnotatedValue::class.java,
        rawType = AnnotatedValue::class.java,
        annotations = emptyList(),
        defaultValue = null,
    )

    @ConfigSerializeWith(CountingSerializer::class)
    private data class AnnotatedValue(val text: String)

    private class FieldOwner {
        @ConfigSerializeWith(FieldSerializer::class)
        lateinit var value: AnnotatedValue
    }

    private class CountingSerializer : ConfigSerializer<AnnotatedValue> {
        init {
            instances++
        }

        override fun serialize(value: AnnotatedValue, context: ConfigSerializationContext): Any = "annotation"

        override fun deserialize(value: Any?, context: ConfigSerializationContext): AnnotatedValue =
            AnnotatedValue(value.toString())

        companion object {
            var instances: Int = 0
        }
    }

    private class FieldSerializer : ConfigSerializer<AnnotatedValue> {
        override fun serialize(value: AnnotatedValue, context: ConfigSerializationContext): Any = "field"

        override fun deserialize(value: Any?, context: ConfigSerializationContext): AnnotatedValue =
            AnnotatedValue(value.toString())
    }

    private class TextSerializer(private val result: String) : ConfigSerializer<AnnotatedValue> {
        override fun serialize(value: AnnotatedValue, context: ConfigSerializationContext): Any = result

        override fun deserialize(value: Any?, context: ConfigSerializationContext): AnnotatedValue =
            AnnotatedValue(value.toString())
    }
}
