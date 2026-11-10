package org.pnfolder.pnlibrary.config.yaml

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.config.ConfigCodec
import org.pnfolder.pnlibrary.api.config.ConfigIgnore
import org.pnfolder.pnlibrary.api.config.ConfigNamingStrategy
import org.pnfolder.pnlibrary.api.config.ConfigOrder
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import org.pnfolder.pnlibrary.spi.platform.PlatformPnLogger
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class ConfigReflectionCostTest {
    open class Base {
        var baseName = "x"

        @ConfigOrder(5) var late = 1
        @Transient var skipped = 2
    }

    class Child : Base() {
        @ConfigIgnore var ignored = 3

        @ConfigOrder(-1) var first = 4
        var port = 5
        val list = listOf(1)

        companion object {
            @JvmField var shared = 6
        }
    }

    /** The discovery as it was before the cache: every call reads the declared fields of the whole hierarchy. */
    private fun discoveredAgain(type: Class<*>): List<Field> = generateSequence(type) { it.superclass }
        .takeWhile { it != Any::class.java }.toList().asReversed()
        .flatMap { it.declaredFields.asList() }
        .filterNot { field ->
            field.isSynthetic || Modifier.isStatic(field.modifiers) || Modifier.isTransient(field.modifiers) ||
                field.isAnnotationPresent(ConfigIgnore::class.java)
        }
        .sortedWith(compareBy<Field> { it.getAnnotation(ConfigOrder::class.java)?.value ?: 0 })

    @Test
    fun `cached fields equal a fresh discovery`() {
        val introspector = ConfigObjectIntrospector(ConfigNamingStrategy.KEBAB_CASE)
        listOf(Base::class.java, Child::class.java, Any::class.java).forEach { type ->
            assertEquals(discoveredAgain(type), introspector.fields(type), type.name)
        }
        assertEquals(listOf("first", "baseName", "port", "list", "late"), introspector.fields(Child::class.java).map { it.name })
    }

    @Test
    fun `declared fields are read once per class however often they are asked`() {
        class Fresh : Base()
        val introspector = ConfigObjectIntrospector(ConfigNamingStrategy.KEBAB_CASE)
        val before = ConfigObjectIntrospector.declaredFieldReads.get()

        repeat(5_000) { introspector.fields(Fresh::class.java) }
        repeat(100) { ConfigObjectIntrospector(ConfigNamingStrategy.SNAKE_CASE).fields(Fresh::class.java) }

        assertEquals(2, ConfigObjectIntrospector.declaredFieldReads.get() - before) // Fresh and Base
    }

    private class CountingCodec : ConfigCodec<Int> {
        val decodes = AtomicInteger()
        override fun encode(value: Int) = "port: $value\n"
        override fun decode(yaml: String): Int {
            decodes.incrementAndGet()
            return Regex("port: ([0-9]+)").find(yaml)?.groupValues?.get(1)?.toInt() ?: error("port is missing")
        }
    }

    private fun yaml(file: Path, codec: CountingCodec) =
        CodeFirstYaml(file.toFile(), 3306, codec, PlatformPnLogger(object : PlatformLogging {}, "test", "test"))

    @Test
    fun `saving decodes the document once and writes the same file`(@TempDir directory: Path) {
        val codec = CountingCodec()
        val file = directory.resolve("config.yml")
        val managed = yaml(file, codec)
        managed.load()
        codec.decodes.set(0)

        managed.save(5432)

        assertEquals(1, codec.decodes.get())
        assertTrue(Files.readString(file).endsWith("port: 5432\n"))
        assertEquals(5432, managed.get())
    }

    @Test
    fun `creating the file from defaults decodes once`(@TempDir directory: Path) {
        val codec = CountingCodec()
        val file = directory.resolve("config.yml")

        yaml(file, codec).load()

        assertEquals(1, codec.decodes.get())
        assertTrue(Files.readString(file).endsWith("port: 3306\n"))
    }

    @Test
    fun `rewriting a changed file decodes once and an invalid document is not written`(@TempDir directory: Path) {
        val codec = CountingCodec()
        val file = directory.resolve("config.yml")
        Files.writeString(file, "port: 1\n# note\n")
        codec.decodes.set(0)

        yaml(file, codec).load()

        assertTrue(codec.decodes.get() <= 1, "decoded ${codec.decodes.get()} times")
        val broken = directory.resolve("broken.yml")
        val managed = yaml(broken, codec)
        managed.load()
        val before = Files.readString(broken)
        val failing = object : ConfigCodec<Int> {
            override fun encode(value: Int) = "nonsense\n"
            override fun decode(yaml: String): Int = error("cannot read")
        }
        runCatching { CodeFirstYaml(broken.toFile(), 1, failing, PlatformPnLogger(object : PlatformLogging {}, "t", "t")).save(7) }
        assertEquals(before, Files.readString(broken))
    }
}
