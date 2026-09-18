package ru.privatenull.pnlibrary.core.config.yaml

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.config.ConfigIgnore
import ru.privatenull.pnlibrary.api.config.ConfigKey
import ru.privatenull.pnlibrary.api.config.ConfigNamingStrategy
import ru.privatenull.pnlibrary.api.config.ConfigOrder

class ConfigObjectIntrospectorTest {
    @Test
    fun `discovers inherited fields and applies filtering ordering and naming`() {
        val introspector = ConfigObjectIntrospector(ConfigNamingStrategy.KEBAB_CASE)

        val fields = introspector.fields(ChildConfig::class.java)

        assertEquals(listOf("base-value", "explicit", "child-value"), fields.map(introspector::key))
        assertEquals(listOf(1, 2, 3), fields.map { introspector.read(it, ChildConfig()) })
    }

    @Test
    fun `materializes standard collection interfaces predictably`() {
        val introspector = ConfigObjectIntrospector(ConfigNamingStrategy.AS_DECLARED)

        val set = introspector.collection(Set::class.java, listOf("a", "a", "b"), "values")
        val map = introspector.map(Map::class.java, linkedMapOf("a" to 1), "values")

        assertEquals(linkedSetOf("a", "b"), set)
        assertEquals(linkedMapOf("a" to 1), map)
    }

    private open class BaseConfig {
        @ConfigOrder(-10)
        var baseValue: Int = 1

        @ConfigIgnore
        var ignored: Int = 0

        @Transient
        var transientValue: Int = 0
    }

    private class ChildConfig : BaseConfig() {
        @ConfigOrder(-5)
        @ConfigKey("explicit")
        var renamed: Int = 2

        var childValue: Int = 3

        companion object {
            @JvmField
            val staticValue: Int = 0
        }
    }
}
