package org.pnfolder.pnlibrary.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.config.ConfigAddress
import org.pnfolder.pnlibrary.api.config.ConfigId
import org.pnfolder.pnlibrary.api.config.ConfigScope
import org.pnfolder.pnlibrary.api.config.ConfigSerializationContext
import org.pnfolder.pnlibrary.api.config.ConfigSerializer
import org.pnfolder.pnlibrary.api.config.ConfigTypeConflict
import org.pnfolder.pnlibrary.api.config.serializer
import org.pnfolder.pnlibrary.api.config.serializerFor
import org.pnfolder.pnlibrary.api.config.type
import org.pnfolder.pnlibrary.api.config.yaml
import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.nio.file.Files
import java.nio.file.Path

data class Place(val world: String = "none", val x: Int = 0)

/** Writes a place as `world,x`. */
class PlaceSerializer(private val tag: String = "") : ConfigSerializer<Place> {
    override fun serialize(value: Place, context: ConfigSerializationContext): Any = "$tag${value.world},${value.x}"
    override fun deserialize(value: Any?, context: ConfigSerializationContext): Place {
        val parts = value.toString().removePrefix(tag).split(',')
        return Place(parts[0], parts[1].toInt())
    }
}

class PlaceFile {
    var spawn: Place = Place("lobby", 1)
}

/** Serializers and words that one plugin adds to other plugins' files: to all of them, one plugin, one module or one file. */
class ConfigAdditionsTest {
    @TempDir lateinit var server: Path

    private val plugins: Path get() = server.resolve("plugins")

    private fun scope(service: YamlConfigurations, plugin: String, module: String = "main"): ConfigScope {
        val folder = plugins.resolve(plugin).also { Files.createDirectories(it) }
        return service.scope(FakeConfigOwner(folder.toFile()), PluginId.of("$plugin-$module"), PluginId.of(plugin), ModuleId.of(module))
    }

    private fun write(plugin: String, name: String, text: String) {
        val file = plugins.resolve(plugin).resolve(name)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }

    private fun spawnOf(scope: ConfigScope, file: String = "places.yml"): Place =
        scope.yaml<PlaceFile>(file) { PlaceFile() }.load().value.spawn

    private fun spawnOrNull(scope: ConfigScope, file: String = "places.yml"): Place? = runCatching { spawnOf(scope, file) }.getOrNull()

    @Test
    fun `the address star covers every file`() {
        val all = ConfigAddress.parse("*")

        assertEquals(ConfigAddress.Level.ALL, all.level)
        assertTrue(all.isAll)
        assertEquals("*", all.toString())
        assertTrue(all.matches(ConfigId.parse("quests/main:a.yml")))
        assertTrue(all.matches(ConfigId.parse("shop/extra:b.yml")))
        assertTrue(all.overlaps(ConfigAddress.parse("shop")))
        assertFalse(ConfigAddress.parse("shop") == all)
        assertEquals(ConfigAddress.everywhere(), all)
        // A plugin named "all" is an ordinary plugin and is not mixed up with everything
        assertFalse(ConfigAddress.plugin("all") == all)
        assertFalse(ConfigAddress.plugin("all").isAll)
        assertThrows(IllegalStateException::class.java) { all.plugin }
        assertThrows(IllegalArgumentException::class.java) { ConfigId.parse("*") }
    }

    @Test
    fun `a written file shows whether the serializer reaches it and a registration can be switched`() {
        YamlConfigurations(FakeOwnerPlatform).use { service ->
            val scope = scope(service, "shop")
            val first = scope.yaml<PlaceFile>("a.yml") { PlaceFile() }.also { it.load() }
            val second = scope.yaml<PlaceFile>("b.yml") { PlaceFile() }.also { it.load() }
            fun text(name: String) = Files.readString(plugins.resolve("shop/$name"))

            scope.serializerFor(PlaceSerializer()).into(first).register().use {
                first.resetToDefaults()
                second.resetToDefaults()
                assertTrue(text("a.yml").contains("spawn: lobby,1"), text("a.yml"))
                assertFalse(text("b.yml").contains("spawn: lobby,1"), text("b.yml"))
            }

            // Closed: the file is written the old way again
            first.resetToDefaults()
            assertFalse(text("a.yml").contains("spawn: lobby,1"), text("a.yml"))

            scope.serializerFor(PlaceSerializer()).into(scope).register()
            second.resetToDefaults()
            assertTrue(text("b.yml").contains("spawn: lobby,1"), text("b.yml"))
        }
    }

    @Test
    fun `an address is built from plugin, module and file without writing a string`() {
        val module = ConfigAddress.plugin("shop").inModule("main")
        val file = module.inFile("rewards.yml")

        assertEquals("shop/main:rewards.yml", file.toString())
        assertEquals(ConfigAddress.parse("shop/main"), module)
        assertEquals(ConfigAddress.parse("shop/main:@server/bukkit.yml"), module.inFile("bukkit.yml", org.pnfolder.pnlibrary.api.config.ConfigRoot.SERVER))
        assertEquals(ConfigAddress.Level.FILE, file.level)
        assertThrows(IllegalArgumentException::class.java) { ConfigAddress.plugin("shop").inFile("a.yml") }
        assertThrows(IllegalArgumentException::class.java) { file.inModule("other") }
    }

    @Test
    fun `into takes a plugin, a scope or a catalog entry as they are`() {
        YamlConfigurations(FakeOwnerPlatform).use { service ->
            write("shop", "places.yml", "spawn: arena,7\n")
            write("other", "places.yml", "spawn: hub,3\n")
            write("third", "places.yml", "spawn: end,9\n")
            val shop = scope(service, "shop")
            val other = scope(service, "other")
            val third = scope(service, "third")
            val thirdFile = third.yaml<PlaceFile>("places.yml") { PlaceFile() }
            val publisher = scope(service, "quests")

            publisher.serializerFor(PlaceSerializer()).into(PluginId.of("shop")).register()
            publisher.serializerFor(PlaceSerializer()).into(other).register()
            publisher.serializerFor(PlaceSerializer()).into(service.all().single { it.plugin.value == "third" }).register()

            assertEquals(Place("arena", 7), spawnOf(shop))
            assertEquals(Place("hub", 3), spawnOf(other))
            assertEquals(Place("end", 9), thirdFile.reload().value.spawn)
        }
    }

    @Test
    fun `without an address a serializer works only in its own module`() {
        YamlConfigurations(FakeOwnerPlatform).use { service ->
            write("shop", "places.yml", "spawn: arena,7\n")
            scope(service, "quests").serializerFor(PlaceSerializer()).register()

            // Nobody told shop how to read a place, so it is read as a section and the line does not fit
            assertFalse(spawnOrNull(scope(service, "shop")) == Place("arena", 7))
        }
    }

    @Test
    fun `a serializer added to a plugin works in its files`() {
        YamlConfigurations(FakeOwnerPlatform).use { service ->
            write("shop", "places.yml", "spawn: arena,7\n")
            val shop = scope(service, "shop")
            val other = scope(service, "other")
            scope(service, "quests").serializerFor(PlaceSerializer()).into("shop").register()
            write("other", "places.yml", "spawn: arena,7\n")

            assertEquals(Place("arena", 7), spawnOf(shop))
            assertFalse(spawnOrNull(other) == Place("arena", 7), "another plugin is not covered")
        }
    }

    @Test
    fun `a serializer added to everything works in every plugin`() {
        YamlConfigurations(FakeOwnerPlatform).use { service ->
            write("shop", "places.yml", "spawn: arena,7\n")
            write("other", "places.yml", "spawn: hub,3\n")
            scope(service, "quests").serializerFor(PlaceSerializer()).everywhere().register()

            assertEquals(Place("arena", 7), spawnOf(scope(service, "shop")))
            assertEquals(Place("hub", 3), spawnOf(scope(service, "other")))
            assertEquals(listOf("quests"), service.serializers().map { it.owner.value })
        }
    }

    @Test
    fun `a serializer can be limited to a module or one file`() {
        YamlConfigurations(FakeOwnerPlatform).use { service ->
            write("shop", "one.yml", "spawn: a,1\n")
            write("shop", "two.yml", "spawn: b,2\n")
            write("shop", "three.yml", "spawn: c,3\n")
            val main = scope(service, "shop")
            val extra = scope(service, "shop", "extra")
            val publisher = scope(service, "quests")
            publisher.serializerFor(PlaceSerializer()).into("shop/main:one.yml").register()

            assertEquals(Place("a", 1), spawnOf(main, "one.yml"))
            assertFalse(spawnOrNull(main, "two.yml") == Place("b", 2), "another file of the module is not covered")

            publisher.serializerFor(PlaceSerializer("x:")).into("shop/extra").register()
            Files.writeString(plugins.resolve("shop/three.yml"), "spawn: x:c,3\n")
            assertEquals(Place("c", 3), spawnOf(extra, "three.yml"))
        }
    }

    @Test
    fun `the module's own serializer is not replaced and the higher priority wins among additions`() {
        YamlConfigurations(FakeOwnerPlatform).use { service ->
            write("shop", "places.yml", "spawn: own:arena,7\n")
            val shop = scope(service, "shop").serializer(PlaceSerializer("own:"))
            scope(service, "quests").serializerFor(PlaceSerializer("low:")).priority(1).everywhere().register()

            assertEquals(Place("arena", 7), spawnOf(shop))

            write("other", "places.yml", "spawn: high:hub,3\n")
            scope(service, "pnmaps").serializerFor(PlaceSerializer("high:")).priority(9).everywhere().register()
            assertEquals(Place("hub", 3), spawnOf(scope(service, "other")))
        }
    }

    @Test
    fun `ordinary types cannot be taken over for other plugins`() {
        YamlConfigurations(FakeOwnerPlatform).use { service ->
            val publisher = scope(service, "quests")
            val text = object : ConfigSerializer<String> {
                override fun serialize(value: String, context: ConfigSerializationContext): Any = value.reversed()
                override fun deserialize(value: Any?, context: ConfigSerializationContext): String = value.toString().reversed()
            }

            assertThrows(IllegalArgumentException::class.java) { publisher.serializerFor(String::class.java, text).everywhere().register() }
            assertThrows(IllegalArgumentException::class.java) { publisher.serializerFor(String::class.java, text).into("shop").register() }
            // Its own module may do what it likes
            publisher.serializerFor(String::class.java, text).register()
        }
    }

    @Test
    fun `closing the registration or the publisher takes the serializer back`() {
        YamlConfigurations(FakeOwnerPlatform).use { service ->
            write("shop", "places.yml", "spawn: arena,7\n")
            val publisher = scope(service, "quests")
            val registration = publisher.serializerFor(PlaceSerializer()).into("shop").register()

            assertEquals(1, service.serializers().size)
            assertThrows(IllegalStateException::class.java) { publisher.serializerFor(PlaceSerializer()).into("shop/main").register() }
            registration.close()
            assertTrue(service.serializers().isEmpty())
            assertFalse(registration.isActive)

            publisher.serializerFor(PlaceSerializer()).into("shop").register()
            publisher.close()
            assertTrue(service.serializers().isEmpty())
        }
    }

    @Test
    fun `a word added for everything works in every plugin but never replaces a word of the file's plugin`() {
        YamlConfigurations(FakeOwnerPlatform).use { service ->
            write("quests", "awards.yml", "awards:\n  - type: item\n    material: GOLD\n")
            write("shop", "awards.yml", "awards:\n  - type: item\n    material: GOLD\n")
            val quests = scope(service, "quests")
            val shop = scope(service, "shop")
            // quests has its own meaning of the word item
            quests.type<Award, GoldAward>().name("item").register()
            scope(service, "questitems").type<Award, ItemAward>().name("item").everywhere().register()

            val own = quests.yaml<AwardsFile>("awards.yml") { AwardsFile() }.load().value.awards.single()
            val added = shop.yaml<AwardsFile>("awards.yml") { AwardsFile() }.load().value.awards.single()

            assertTrue(own is GoldAward, "the owner's word wins in its own file: $own")
            assertEquals(ItemAward("GOLD", 1), added)
        }
    }

    private fun first(scope: ConfigScope): Award {
        val file = scope.get("awards.yml") ?: scope.yaml<AwardsFile>("awards.yml") { AwardsFile() }
        @Suppress("UNCHECKED_CAST")
        return (file as org.pnfolder.pnlibrary.api.config.ManagedConfig<AwardsFile>).reload().value.awards.first()
    }

    private fun item() = "awards:\n  - type: pick\n    material: GOLD\n"

    @Test
    fun `a taken word fails by default, is kept on request and is replaced on request`() {
        YamlConfigurations(FakeOwnerPlatform).use { service ->
            write("quests", "awards.yml", item())
            val quests = scope(service, "quests")
            val library = scope(service, "lib")
            library.type<Award, ItemAward>().name("pick").everywhere().register()

            assertThrows(IllegalArgumentException::class.java) {
                library.type<Award, GoldAward>().name("pick").everywhere().register()
            }
            val kept = library.type<Award, GoldAward>().name("pick").everywhere().onConflict(ConfigTypeConflict.KEEP).register()
            assertFalse(kept.isActive)
            assertTrue(first(quests) is ItemAward)

            val replaced = library.type<Award, GoldAward>().name("pick").everywhere().onConflict(ConfigTypeConflict.REPLACE).register()
            assertTrue(replaced.isActive)
            assertTrue(first(quests) is GoldAward)
        }
    }

    @Test
    fun `one word can be published for several places and withdrawn together`() {
        YamlConfigurations(FakeOwnerPlatform).use { service ->
            write("quests", "awards.yml", item())
            write("shop", "awards.yml", item())
            write("farm", "awards.yml", item())
            val publisher = scope(service, "lib")

            val handles = publisher.type<Award, ItemAward>().name("pick")
                .registerEach(listOf(ConfigAddress.plugin("quests"), ConfigAddress.plugin("shop")))

            assertEquals(2, handles.size)
            assertTrue(first(scope(service, "quests")) is ItemAward)
            assertTrue(first(scope(service, "shop")) is ItemAward)
            assertTrue(runCatching { first(scope(service, "farm")) }.getOrNull() !is ItemAward)
            handles.forEach { it.close() }
            assertTrue(runCatching { first(scope(service, "quests")) }.getOrNull() !is ItemAward)
        }
    }
}
