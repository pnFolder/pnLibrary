package org.pnfolder.pnlibrary.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.config.ConfigId
import org.pnfolder.pnlibrary.api.config.ConfigOptions
import org.pnfolder.pnlibrary.api.config.ConfigPolymorphic
import org.pnfolder.pnlibrary.api.config.ConfigRoot
import org.pnfolder.pnlibrary.api.config.ConfigScope
import org.pnfolder.pnlibrary.api.config.ConfigType
import org.pnfolder.pnlibrary.api.config.ConfigTypes
import org.pnfolder.pnlibrary.api.config.UnknownTypePolicy
import org.pnfolder.pnlibrary.api.config.extensions
import org.pnfolder.pnlibrary.api.config.type
import org.pnfolder.pnlibrary.api.config.yaml
import org.pnfolder.pnlibrary.api.config.yamlAt
import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.nio.file.Files
import java.nio.file.Path

@ConfigPolymorphic(discriminator = "type")
@ConfigTypes(ConfigType(MoneyAward::class, "money"))
interface Award

data class MoneyAward(var amount: Int = 0) : Award
data class ItemAward(var material: String = "STONE", var count: Int = 1) : Award
data class GoldAward(var grams: Int = 1) : Award

data class AwardsFile(var awards: List<Award> = listOf(MoneyAward(5)))

/** Several plugins in one server folder: `plugins/<name>/` for each, so `PLUGINS` and `SERVER` roots exist. */
class ConfigCatalogTest {
    @TempDir lateinit var server: Path

    private val plugins: Path get() = server.resolve("plugins")

    private fun scope(
        service: ConfigurationServiceImpl, plugin: String, module: String = "main",
    ): ConfigScope {
        val folder = plugins.resolve(plugin).also { Files.createDirectories(it) }
        return service.scope(FakeConfigOwner(folder.toFile()), PluginId.of("$plugin-$module"), PluginId.of(plugin), ModuleId.of(module))
    }

    private fun write(plugin: String, name: String, text: String) {
        val file = plugins.resolve(plugin).resolve(name)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }

    private val skipping = ConfigOptions.builder().unknownTypes(UnknownTypePolicy.SKIP).build()

    // ── Identifiers and lookup ────────────────────────────────────────────────────────────────

    @Test
    fun `registered files are listed under plugin, module and path`() {
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val main = scope(service, "quests")
            val extra = scope(service, "quests", "extra")
            scope(service, "shop").yaml<AwardsFile>("prices.yml") { AwardsFile() }
            val awards = main.yaml<AwardsFile>("awards.yml") { AwardsFile() }
            extra.yaml<AwardsFile>("rewards/daily.yml") { AwardsFile() }

            assertEquals(
                listOf("shop/main:prices.yml", "quests/main:awards.yml", "quests/extra:rewards/daily.yml"),
                service.all().map { it.id.toString() },
            )
            assertEquals(2, service.find("quests").size)
            assertEquals(listOf("quests/main:awards.yml"), service.find("quests/main").map { it.id.toString() })
            assertEquals(1, service.find("quests/extra:rewards/daily.yml").size)
            assertTrue(service.find("nobody").isEmpty())
            assertEquals(awards.id, ConfigId.of("quests", "main", "awards.yml"))
            assertSame(awards, service.get("quests/main:awards.yml"))
            assertSame(awards, main.get("awards.yml"))
            assertNull(service.get("quests/main:missing.yml"))
            assertEquals(AwardsFile::class.java, service.require("quests/main:awards.yml").info?.modelType)
            assertEquals(3, service.byModel(AwardsFile::class.java).size)
            assertTrue(service.byModel(String::class.java).isEmpty())
        }
    }

    @Test
    fun `a scope lists only its own files and require names the registered ones`() {
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val main = scope(service, "quests")
            scope(service, "quests", "extra").yaml<AwardsFile>("other.yml") { AwardsFile() }
            main.yaml<AwardsFile>("awards.yml") { AwardsFile() }

            assertEquals(listOf("awards.yml"), main.list().map { it.path })
            assertEquals("quests", main.plugin.value)
            assertEquals("main", main.module.value)
            val error = assertThrows(IllegalStateException::class.java) { main.require("nope.yml") }
            assertTrue(error.message.orEmpty().contains("awards.yml"), error.message)
            assertThrows(IllegalStateException::class.java) { service.require("quests/main:nope.yml") }
        }
    }

    @Test
    fun `info describes a file without values and follows its life`() {
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val config = scope(service, "quests").yaml<AwardsFile>("awards.yml") { AwardsFile() }

            val before = requireNotNull(config.info)
            assertFalse(before.exists)
            assertFalse(before.isLoaded)
            assertNull(before.loadedAt)
            config.load()
            val after = requireNotNull(config.info)
            assertTrue(after.exists)
            assertTrue(after.isLoaded)
            assertNotNull(after.loadedAt)
            assertEquals(plugins.resolve("quests").resolve("awards.yml"), after.file)
            config.unload()
            assertFalse(requireNotNull(config.info).isLoaded)
        }
    }

    @Test
    fun `closing a scope removes its files from the catalog`() {
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val scope = scope(service, "quests")
            scope.yaml<AwardsFile>("awards.yml") { AwardsFile() }
            assertEquals(1, service.all().size)
            scope.close()
            assertTrue(service.all().isEmpty())
        }
    }

    // ── Types added to another plugin's files ─────────────────────────────────────────────────

    @Test
    fun `a plugin teaches another plugin's file a new word, and the file reads it`() {
        write("quests", "awards.yml", "awards:\n  - type: money\n    amount: 7\n  - type: item\n    material: DIAMOND\n    count: 3\n")
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val awards = scope(service, "quests").yaml<AwardsFile>("awards.yml") { AwardsFile() }
            scope(service, "questitems").type<Award, ItemAward>().name("item").into("quests/main:awards.yml").register()

            assertEquals(listOf(MoneyAward(7), ItemAward("DIAMOND", 3)), awards.load().value.awards)
        }
    }

    @Test
    fun `without the word the file fails by default and names the element`() {
        write("quests", "awards.yml", "awards:\n  - type: item\n    material: DIAMOND\n")
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val awards = scope(service, "quests").yaml<AwardsFile>("awards.yml") { AwardsFile() }

            val error = assertThrows(IllegalArgumentException::class.java) { awards.load() }
            assertTrue(error.message.orEmpty().contains("item"), error.message)
        }
    }

    @Test
    fun `skipping keeps the element in the file and reads it again when the word appears`() {
        val text = "awards:\n  - type: money\n    amount: 7\n  - type: item\n    material: DIAMOND\n    count: 3\n"
        write("quests", "awards.yml", text)
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val awards = scope(service, "quests").yaml("awards.yml", skipping) { AwardsFile() }

            assertEquals(listOf<Award>(MoneyAward(7)), awards.load().value.awards)
            assertEquals(1, requireNotNull(awards.info).skippedUnknownTypes.size)
            assertTrue(Files.readString(plugins.resolve("quests").resolve("awards.yml")).contains("DIAMOND"), "the element stays in the file")
            // Saving would drop the skipped element from the file, so it is refused.
            assertThrows(IllegalStateException::class.java) { awards.save() }
            assertThrows(IllegalStateException::class.java) { awards.update { it } }

            scope(service, "questitems").type<Award, ItemAward>().name("item").into("quests/main:awards.yml").register()

            // Registering the word read the file again by itself.
            assertEquals(listOf(MoneyAward(7), ItemAward("DIAMOND", 3)), awards.get().awards)
            assertTrue(requireNotNull(awards.info).skippedUnknownTypes.isEmpty())
            awards.save()
            assertTrue(Files.readString(plugins.resolve("quests").resolve("awards.yml")).contains("DIAMOND"))
        }
    }

    @Test
    fun `a word added to a module covers its files and no other module`() {
        val text = "awards:\n  - type: item\n"
        write("quests", "a.yml", text)
        write("quests", "b.yml", text)
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val main = scope(service, "quests")
            val extra = scope(service, "quests", "extra")
            val inMain = main.yaml("a.yml", skipping) { AwardsFile() }
            val inExtra = extra.yaml("b.yml", skipping) { AwardsFile() }
            scope(service, "questitems").type<Award, ItemAward>().name("item").into("quests/main").register()

            assertEquals(1, inMain.loadValue().awards.size)
            assertTrue(inExtra.loadValue().awards.isEmpty(), "quests/extra is not covered")
        }
    }

    @Test
    fun `a word added to a plugin covers every module of it`() {
        val text = "awards:\n  - type: item\n"
        write("quests", "a.yml", text)
        write("quests", "b.yml", text)
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            scope(service, "questitems").type<Award, ItemAward>().name("item").into("quests").register()
            val inMain = scope(service, "quests").yaml<AwardsFile>("a.yml") { AwardsFile() }
            val inExtra = scope(service, "quests", "extra").yaml<AwardsFile>("b.yml") { AwardsFile() }

            assertEquals(1, inMain.loadValue().awards.size)
            assertEquals(1, inExtra.loadValue().awards.size)
        }
    }

    @Test
    fun `a word can be aimed at a registered file object`() {
        write("quests", "awards.yml", "awards:\n  - type: stack\n")
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val awards = scope(service, "quests").yaml<AwardsFile>("awards.yml") { AwardsFile() }
            scope(service, "questitems").type<Award, ItemAward>().name("item").alias("stack").into(awards).register()

            assertEquals(1, awards.loadValue().awards.size)
        }
    }

    @Test
    fun `a word stops working after the plugin that added it is closed`() {
        write("quests", "awards.yml", "awards:\n  - type: item\n")
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val awards = scope(service, "quests").yaml("awards.yml", skipping) { AwardsFile() }
            val items = scope(service, "questitems")
            items.type<Award, ItemAward>().name("item").into("quests/main:awards.yml").register()
            assertEquals(1, awards.loadValue().awards.size)

            items.close()

            assertTrue(awards.reloadValue().awards.isEmpty())
        }
    }

    @Test
    fun `a file only sees words that cover it`() {
        write("quests", "awards.yml", "awards:\n  - type: item\n")
        write("shop", "awards.yml", "awards:\n  - type: item\n")
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            scope(service, "questitems").type<Award, ItemAward>().name("item").into("quests").register()
            val shop = scope(service, "shop").yaml("awards.yml", skipping) { AwardsFile() }

            assertTrue(shop.loadValue().awards.isEmpty())
        }
    }

    @Test
    fun `without into a word works in the module that registers it`() {
        write("quests", "awards.yml", "awards:\n  - type: item\n")
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val main = scope(service, "quests")
            main.type<Award, ItemAward>().name("item").register()

            assertEquals(1, main.yaml<AwardsFile>("awards.yml") { AwardsFile() }.loadValue().awards.size)
        }
    }

    @Test
    fun `types lists the words of a base type and who added them`() {
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            scope(service, "questitems").type<Award, ItemAward>().name("item").alias("stack").priority(4).into("quests").register()

            val words = service.types(Award::class.java)
            assertEquals(listOf("money", "item"), words.map { it.name })
            assertNull(words[0].owner)
            assertEquals("questitems", words[1].owner?.value)
            assertEquals(setOf("stack"), words[1].aliases)
            assertEquals(4, words[1].priority)
            assertEquals("quests", words[1].target.toString())
        }
    }

    // ── Protection ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a word that the base type declares cannot be taken`() {
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                scope(service, "evil").type<Award, ItemAward>().name("money").into("quests").register()
            }
            assertTrue(error.message.orEmpty().contains("money"), error.message)
            assertThrows(IllegalArgumentException::class.java) {
                scope(service, "evil").type<Award, ItemAward>().name("fine").alias("MONEY").into("quests").register()
            }
        }
    }

    @Test
    fun `a word that the file's own plugin published cannot be taken by another plugin`() {
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            scope(service, "quests").type<Award, GoldAward>().name("gold").register()

            assertThrows(IllegalArgumentException::class.java) {
                scope(service, "questitems").type<Award, ItemAward>().name("gold").into("quests").register()
            }
            // A different word is fine.
            scope(service, "questitems").type<Award, ItemAward>().name("item").into("quests").register()
        }
    }

    @Test
    fun `one plugin cannot publish the same word twice for overlapping files`() {
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val items = scope(service, "questitems")
            items.type<Award, ItemAward>().name("item").into("quests").register()

            assertThrows(IllegalArgumentException::class.java) {
                items.type<Award, ItemAward>().name("item").into("quests/main:awards.yml").register()
            }
            // Files that no earlier registration covers are fine.
            items.type<Award, ItemAward>().name("item").into("shop").register()
        }
    }

    @Test
    fun `two plugins may add one word and a priority decides`() {
        write("quests", "awards.yml", "awards:\n  - type: thing\n")
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val awards = scope(service, "quests").yaml<AwardsFile>("awards.yml") { AwardsFile() }
            scope(service, "one").type<Award, ItemAward>().name("thing").priority(1).into("quests").register()
            scope(service, "two").type<Award, GoldAward>().name("thing").priority(9).into("quests").register()

            assertTrue(awards.loadValue().awards.single() is GoldAward)
        }
    }

    @Test
    fun `a module can refuse additions from plugins and allow only some`() {
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val quests = scope(service, "quests")
            quests.extensions { allow("questitems") }

            scope(service, "questitems").type<Award, ItemAward>().name("item").into("quests").register()
            val error = assertThrows(IllegalStateException::class.java) {
                scope(service, "stranger").type<Award, GoldAward>().name("gold").into("quests/main").register()
            }
            assertTrue(error.message.orEmpty().contains("stranger"), error.message)
        }
    }

    @Test
    fun `a module that closes itself later hides words that were added before`() {
        write("quests", "awards.yml", "awards:\n  - type: item\n")
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val quests = scope(service, "quests")
            val awards = quests.yaml("awards.yml", skipping) { AwardsFile() }
            scope(service, "questitems").type<Award, ItemAward>().name("item").into("quests").register()
            assertEquals(1, awards.loadValue().awards.size)

            quests.extensions { denyAll() }

            assertTrue(awards.reloadValue().awards.isEmpty())
        }
    }

    // ── Roots and read-only files ─────────────────────────────────────────────────────────────

    @Test
    fun `read-only reads a file as it is and never writes it`() {
        val text = "awards:\n  - type: money\n    amount: 9\n# keep me\n"
        write("quests", "awards.yml", text)
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val options = ConfigOptions.builder().readOnly().build()
            val awards = scope(service, "quests").yaml("awards.yml", options) { AwardsFile() }

            assertEquals(MoneyAward(9), awards.loadValue().awards.single())
            assertEquals(text, Files.readString(plugins.resolve("quests").resolve("awards.yml")))
            assertThrows(IllegalStateException::class.java) { awards.save() }
            assertTrue(requireNotNull(awards.info).readOnly)
        }
    }

    @Test
    fun `read-only does not create a missing file`() {
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val awards = scope(service, "quests").yaml("awards.yml", ConfigOptions.builder().readOnly().build()) { AwardsFile() }

            assertThrows(IllegalStateException::class.java) { awards.load() }
            assertFalse(Files.exists(plugins.resolve("quests").resolve("awards.yml")))
        }
    }

    @Test
    fun `two plugins may read one file but only one may write it`() {
        write("shop", "prices.yml", "awards: []\n")
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val readOnly = ConfigOptions.builder().readOnly().root(ConfigRoot.PLUGINS).build()
            val writing = ConfigOptions.builder().root(ConfigRoot.PLUGINS).build()
            scope(service, "quests").yaml("shop/prices.yml", readOnly) { AwardsFile() }
            scope(service, "quests", "extra").yaml("shop/prices.yml", readOnly) { AwardsFile() }
            scope(service, "other").yaml("shop/prices.yml", writing) { AwardsFile() }

            val error = assertThrows(IllegalStateException::class.java) {
                scope(service, "third").yaml("shop/prices.yml", writing) { AwardsFile() }
            }
            assertTrue(error.message.orEmpty().contains("already managed"), error.message)
        }
    }

    @Test
    fun `the same file cannot be registered twice by one module`() {
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val quests = scope(service, "quests")
            quests.yaml<AwardsFile>("awards.yml") { AwardsFile() }

            assertThrows(IllegalStateException::class.java) { quests.yaml<AwardsFile>("awards.yml") { AwardsFile() } }
        }
    }

    @Test
    fun `a file in the plugins folder gets the root in its identifier and is never created`() {
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val config = scope(service, "quests").yaml(
                "essentials/config.yml", ConfigOptions.builder().root(ConfigRoot.PLUGINS).build(),
            ) { AwardsFile() }

            assertEquals("quests/main:@plugins/essentials/config.yml", config.id.toString())
            assertSame(config, service.get("quests/main:@plugins/essentials/config.yml"))
            assertSame(config, scope(service, "quests").get("@plugins/essentials/config.yml"))
            assertThrows(IllegalStateException::class.java) { config.load() }
            assertFalse(Files.exists(plugins.resolve("essentials")), "somebody else's folder stays untouched")
        }
    }

    @Test
    fun `a file in the plugins folder is read and backed up before a rewrite`() {
        write("essentials", "config.yml", "awards:\n  - type: money\n    amount: 3\n")
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val config = scope(service, "quests").yaml(
                "essentials/config.yml", ConfigOptions.builder().root(ConfigRoot.PLUGINS).backups(false).build(),
            ) { AwardsFile() }

            assertEquals(MoneyAward(3), config.loadValue().awards.single())
            val backups = Files.list(plugins.resolve("essentials")).use { files -> files.map { it.fileName.toString() }.toList() }
            assertTrue(backups.any { it.contains(".bak") }, "a backup was made although the options turned backups off: $backups")
        }
    }

    @Test
    fun `a file in the server root is found next to the plugins folder`() {
        Files.writeString(server.resolve("bukkit.yml"), "awards: []\n")
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val config = scope(service, "quests").yaml(
                "bukkit.yml", ConfigOptions.builder().root(ConfigRoot.SERVER).readOnly().build(),
            ) { AwardsFile() }

            assertTrue(config.loadValue().awards.isEmpty())
            assertEquals("quests/main:@server/bukkit.yml", config.id.toString())
        }
    }

    @Test
    fun `a path cannot leave its root`() {
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val quests = scope(service, "quests")
            assertThrows(IllegalArgumentException::class.java) { quests.yaml<AwardsFile>("../shop/prices.yml") { AwardsFile() } }
            assertThrows(IllegalArgumentException::class.java) { quests.yaml<AwardsFile>("/etc/x.yml") { AwardsFile() } }
            assertThrows(IllegalArgumentException::class.java) {
                quests.yaml("../../outside.yml", ConfigOptions.builder().root(ConfigRoot.PLUGINS).build()) { AwardsFile() }
            }
            assertThrows(IllegalArgumentException::class.java) { quests.yaml<AwardsFile>("notes.txt") { AwardsFile() } }
        }
    }

    @Test
    fun `files of other plugins are not announced to diagnostics`() {
        write("essentials", "config.yml", "awards: []\n")
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val announced = mutableListOf<String>()
            service.fileListener = { file -> announced += file.path; null }
            val quests = scope(service, "quests")
            quests.yaml<AwardsFile>("own.yml") { AwardsFile() }
            quests.yaml("essentials/config.yml", ConfigOptions.builder().root(ConfigRoot.PLUGINS).readOnly().build()) { AwardsFile() }

            assertEquals(listOf("own.yml"), announced)
        }
    }

    private fun assertSame(expected: Any?, actual: Any?) =
        assertTrue(expected === actual, "expected the same object: $expected vs $actual")

    @Test
    fun `a plugin opens a file at any absolute path and the file is created and listed`(@TempDir elsewhere: Path) {
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val file = elsewhere.resolve("deep/folder/awards.yml")
            val config = scope(service, "quests").yamlAt<AwardsFile>(file) { AwardsFile() }

            config.load()

            assertTrue(Files.exists(file), "created where the plugin asked")
            val id = config.id!!
            assertEquals(ConfigRoot.EXTERNAL, id.root)
            assertEquals(id, ConfigId.parse(id.toString()), id.toString())
            assertEquals(file.toAbsolutePath().normalize(), service.get(id)!!.info!!.file)
        }
    }

    @Test
    fun `an external path must be absolute and a second writer of the same file is refused`(@TempDir elsewhere: Path) {
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val quests = scope(service, "quests")
            assertThrows(IllegalArgumentException::class.java) {
                quests.yaml("relative.yml", ConfigOptions.builder().root(ConfigRoot.EXTERNAL).build()) { AwardsFile() }
            }
            val file = elsewhere.resolve("shared.yml")
            quests.yamlAt<AwardsFile>(file) { AwardsFile() }
            assertThrows(IllegalStateException::class.java) { scope(service, "other").yamlAt<AwardsFile>(file) { AwardsFile() } }
        }
    }
}
