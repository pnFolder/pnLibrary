package org.pnfolder.pnlibrary.core.actions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.pnfolder.pnlibrary.api.actions.Action
import org.pnfolder.pnlibrary.api.actions.ActionContext
import org.pnfolder.pnlibrary.api.actions.builtin.CommandAction
import org.pnfolder.pnlibrary.api.actions.builtin.DelayAction
import org.pnfolder.pnlibrary.api.actions.builtin.MessageAction
import org.pnfolder.pnlibrary.api.actions.builtin.RequireAction
import org.pnfolder.pnlibrary.api.actions.builtin.RunAction
import org.pnfolder.pnlibrary.api.actions.builtin.SoundAction
import org.pnfolder.pnlibrary.api.actions.builtin.TitleAction
import org.pnfolder.pnlibrary.api.audiences.LibraryAudience
import org.pnfolder.pnlibrary.api.audiences.LibraryPlayer
import org.pnfolder.pnlibrary.api.checks.Requirement
import org.pnfolder.pnlibrary.api.checks.builtin.Comparison
import org.pnfolder.pnlibrary.api.checks.builtin.ExperienceCheck
import org.pnfolder.pnlibrary.api.checks.builtin.ItemCheck
import org.pnfolder.pnlibrary.api.checks.builtin.LengthCheck
import org.pnfolder.pnlibrary.api.checks.builtin.NotCheck
import org.pnfolder.pnlibrary.api.checks.builtin.PermissionCheck
import org.pnfolder.pnlibrary.api.checks.builtin.PermissionsCheck
import org.pnfolder.pnlibrary.api.checks.builtin.RegexCheck
import org.pnfolder.pnlibrary.api.checks.builtin.TypeCheck
import org.pnfolder.pnlibrary.api.checks.builtin.ValueCheck
import org.pnfolder.pnlibrary.api.checks.builtin.ValueType
import org.pnfolder.pnlibrary.api.checks.internal.CheckSerializer
import org.pnfolder.pnlibrary.api.checks.internal.RequirementSerializer
import org.pnfolder.pnlibrary.api.config.ConfigOptions
import org.pnfolder.pnlibrary.api.config.ConfigSerializationContext
import org.pnfolder.pnlibrary.api.config.yaml
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.text.ComponentService
import org.pnfolder.pnlibrary.config.ConfigurationServiceImpl
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.logging.Logger

class ActionFormatTest {
    @TempDir
    lateinit var directory: Path

    // ---- conditions ----

    private fun condition(vararg pairs: Pair<String, Any?>) = CheckSerializer.parse(mapOf(*pairs))

    @Test
    fun `permission types read their fields`() {
        assertEquals(PermissionCheck("shop.use"), condition("type" to "has permission", "permission" to "shop.use"))
        assertEquals(
            PermissionsCheck(listOf("a", "b"), 1),
            condition("type" to "has permissions", "permissions" to listOf("a", "b"), "minimum" to 1),
        )
    }

    @Test
    fun `a bang negates and the spelling of keys does not matter`() {
        val negated = condition("type" to "!has permission", "permission" to "shop.use")
        assertEquals(NotCheck(PermissionCheck("shop.use")), negated)

        val item = condition("type" to "has-item", "material" to "diamond", "amount" to 3, "name-contains" to "Key", "loreContains" to "Rare")
        assertEquals(ItemCheck("diamond", 3, nameContains = "Key", loreContains = "Rare", ignoreCase = false), item)
    }

    @Test
    fun `string types and comparators read input and output`() {
        assertEquals(
            ValueCheck("%player_world%", Comparison.EQUALS, "world", ignoreCase = false),
            condition("type" to "string equals", "input" to "%player_world%", "output" to "world"),
        )
        assertEquals(
            ValueCheck("a", Comparison.EQUALS, "b", ignoreCase = true),
            condition("type" to "string equals ignorecase", "input" to "a", "output" to "b"),
        )
        assertEquals(
            ValueCheck("%vault_eco_balance%", Comparison.GREATER_OR_EQUAL, "100"),
            condition("type" to ">=", "input" to "%vault_eco_balance%", "output" to 100),
        )
        assertEquals(ValueCheck("x", Comparison.NUMBER_EQUALS, "5"), condition("type" to "==", "input" to "x", "output" to 5))
    }

    @Test
    fun `other types read their fields`() {
        assertEquals(ExperienceCheck(30, true), condition("type" to "has exp", "amount" to 30, "level" to true))
        assertEquals(LengthCheck("x", 2, 8), condition("type" to "string length", "input" to "x", "min" to 2, "max" to 8))
        assertEquals(TypeCheck("x", ValueType.UUID), condition("type" to "is object", "input" to "x", "object" to "uuid"))
        assertEquals(RegexCheck("x", "[a-z]+"), condition("type" to "regex matches", "input" to "x", "regex" to "[a-z]+"))
    }

    @Test
    fun `unknown types and missing fields fail with a clear reason`() {
        assertTrue(assertThrows(IllegalArgumentException::class.java) { condition("type" to "has wings") }.message!!.contains("has wings"))
        assertTrue(assertThrows(IllegalArgumentException::class.java) { condition("type" to "has permission") }.message!!.contains("permission"))
        assertThrows(IllegalArgumentException::class.java) { condition("permission" to "x") }
    }

    @Test
    fun `a mistyped type suggests the closest one`() {
        val message = assertThrows(IllegalArgumentException::class.java) { condition("type" to "has permision") }.message!!

        assertTrue(message.contains("Did you mean 'has permission'?"), message)
    }

    @Test
    fun `an unknown key is reported instead of silently ignored`() {
        val message = assertThrows(IllegalArgumentException::class.java) {
            condition("type" to "has permission", "permision" to "shop.use")
        }.message!!

        assertTrue(message.contains("Unknown key 'permision'") && message.contains("Did you mean 'permission'?"), message)
    }

    @Test
    fun `a typo in deny_commands next to a requirement is reported`() {
        val message = assertThrows(IllegalArgumentException::class.java) {
            RequirementSerializer.deserialize(
                mapOf("type" to "has permission", "permission" to "shop.use", "deny_comands" to listOf("[message] no")),
                ConfigSerializationContext("shop.requirements.a", Any::class.java, Any::class.java, emptyList(), null),
            )
        }.message!!

        assertTrue(message.contains("Did you mean 'deny_commands'?"), message)
    }

    @Test
    fun `conditions written back read the same`() {
        listOf(
            PermissionCheck("a.b"),
            PermissionsCheck(listOf("a", "b"), 2),
            ExperienceCheck(5, true),
            LengthCheck("x", 1, 9),
            ValueCheck("x", Comparison.GREATER_THAN, "5"),
            ValueCheck("x", Comparison.EQUALS, "y", ignoreCase = false),
            NotCheck(PermissionCheck("a")),
        ).forEach { original ->
            val written = CheckSerializer.describe(original)!!
            assertEquals(original, CheckSerializer.parse(written), written.toString())
        }
    }

    // ---- a real YAML file ----

    data class ShopConfig(
        var buy: List<Action> = emptyList(),
        var gate: Requirement = Requirement(),
    )

    class Owner(private val folder: File) {
        fun getDataFolder(): File = folder
        fun getLogger(): Logger = Logger.getLogger("action-format-test")
    }

    @Test
    fun `a line of text is rejected at its entry with the object to write instead`() {
        Files.writeString(
            directory.resolve("shop.yml"),
            listOf("buy:", "  - type: message", "    message: fine", "  - \"[console] give %player_name% apple\"")
                .joinToString("\n") + "\n",
        )

        val service = ConfigurationServiceImpl(object : PlatformOwnership {})
        service.use {
            val scope = it.scope(Owner(directory.toFile()), PluginId.of("tester"))
            val error = assertThrows(IllegalArgumentException::class.java) {
                scope.yaml<ShopConfig>("shop.yml", ConfigOptions.DEFAULT) { ShopConfig() }.load()
            }

            val message = error.message!!
            assertTrue(message.contains("at buy[1]"), message)
            assertTrue(message.contains("type: console") && message.contains("command: give %player_name% apple"), message)
        }
    }

    @Test
    fun `a typo inside a requirement is reported once at the requirement`() {
        Files.writeString(
            directory.resolve("shop.yml"),
            listOf(
                "buy:",
                "  - type: requirements",
                "    requirements:",
                "      rich:",
                "        type: has permission",
                "        permission: shop.use",
                "        deny_comands:",
                "          - type: message",
                "            message: no",
            ).joinToString("\n") + "\n",
        )

        val service = ConfigurationServiceImpl(object : PlatformOwnership {})
        service.use {
            val scope = it.scope(Owner(directory.toFile()), PluginId.of("tester"))
            val error = assertThrows(IllegalArgumentException::class.java) {
                scope.yaml<ShopConfig>("shop.yml", ConfigOptions.DEFAULT) { ShopConfig() }.load()
            }

            val message = error.message!!
            assertTrue(message.contains("at buy[0].requirement.requirements.rich"), message)
            assertTrue(message.contains("Did you mean 'deny_commands'?"), message)
            assertEquals(1, Regex("Invalid configuration value at").findAll(message).count(), message)
        }
    }

    @Test
    fun `a minimum above the optional count read from yaml warns and cannot pass`() {
        Files.writeString(
            directory.resolve("shop.yml"),
            listOf(
                "gate:",
                "  minimum_requirements: 2",
                "  requirements:",
                "    a:",
                "      type: has permission",
                "      permission: shop.use",
                "  deny_commands:",
                "    - type: log",
                "      text: no",
            ).joinToString(System.lineSeparator()) + System.lineSeparator(),
        )
        val service = ConfigurationServiceImpl(object : PlatformOwnership {})
        service.use {
            val scope = it.scope(Owner(directory.toFile()), PluginId.of("tester"))
            val gate = scope.yaml<ShopConfig>("shop.yml", ConfigOptions.DEFAULT) { ShopConfig() }.load().value.gate
            val logger = mock(PnLogger::class.java)
            val context = ActionContext(
                player = mock(LibraryPlayer::class.java),
                allPlayers = mock(LibraryAudience::class.java),
                components = mock(ComponentService::class.java),
                logger = logger,
                tasks = mock(TaskScope::class.java),
            )

            assertFalse(gate.test(context))

            val warnings = mockingDetails(logger).invocations.filter { call -> call.method.name == "warning" }
            assertTrue(warnings.any { call -> call.arguments[0].toString().contains("can never pass") }, warnings.toString())
        }
    }

    @Test
    fun `a yaml file with titles, waits, templates and short requirements loads`() {
        Files.writeString(
            directory.resolve("shop.yml"),
            listOf(
                "buy:",
                "  - type: title",
                "    title: '&aWelcome'",
                "    subtitle: '&7Hi'",
                "  - type: delay",
                "    duration: 2s",
                "  - type: run",
                "    name: refuse",
                "  - type: requirements",
                "    requirements:",
                "      rich:",
                "        if: \"%vault_eco_balance% >= 100\"",
                "        deny:",
                "          - type: message",
                "            message: '&cNeed 100'",
                "      sober: \"%drunk% == 0\"",
            ).joinToString("\n") + "\n",
        )

        val service = ConfigurationServiceImpl(object : PlatformOwnership {})
        service.use {
            val scope = it.scope(Owner(directory.toFile()), PluginId.of("tester"))
            val loaded = scope.yaml<ShopConfig>("shop.yml", ConfigOptions.DEFAULT) { ShopConfig() }.load().value

            assertInstanceOf(TitleAction::class.java, loaded.buy[0])
            assertEquals(DelayAction(Duration.ofSeconds(2)), loaded.buy[1])
            assertEquals(RunAction("refuse"), loaded.buy[2])
            val requirements = (loaded.buy[3] as RequireAction).requirement!!
            assertEquals(listOf("rich", "sober"), requirements.entries.keys.toList())
            assertEquals(1, requirements.entries.getValue("rich").deny.size)
            assertEquals(
                ValueCheck("%drunk%", Comparison.NUMBER_EQUALS, "0"),
                requirements.entries.getValue("sober").check,
            )
        }
    }

    @Test
    fun `a yaml file written in the menu style loads`() {
        Files.writeString(
            directory.resolve("shop.yml"),
            """
            buy:
              - type: message
                message: "&aHello %player_name%"
              - type: sound
                sound: ENTITY_PLAYER_LEVELUP
              - type: delay
                duration: 1s
                do:
                  - type: console
                    command: give %player_name% diamond 1
              - type: requirements
                requirements:
                  rich:
                    type: ">="
                    input: "%vault_eco_balance%"
                    output: 100
                    deny_commands:
                      - type: message
                        message: "&cNot enough"
                  vip:
                    type: has permission
                    permission: shop.vip
                    optional: true
                    success_commands:
                      - type: message
                        message: VIP discount
                  not_banned:
                    type: "!has permission"
                    permission: shop.banned
                minimum_requirements: 1
                stop_at_success: true
                deny_commands:
                  - type: sound
                    sound: ENTITY_VILLAGER_NO
                success_commands:
                  - type: console
                    command: eco take %player_name% 100
            gate:
              requirements:
                near:
                  type: is near
                  location: "world,0,64,0"
                  distance: 10
            """.trimIndent() + "\n",
        )

        val service = ConfigurationServiceImpl(object : PlatformOwnership {})
        service.use {
            val scope = it.scope(Owner(directory.toFile()), PluginId.of("tester"))
            val loaded = scope.yaml<ShopConfig>("shop.yml", ConfigOptions.DEFAULT) { ShopConfig() }.load().value

            assertEquals(MessageAction(listOf("&aHello %player_name%")), loaded.buy[0])
            assertInstanceOf(SoundAction::class.java, loaded.buy[1])
            assertInstanceOf(DelayAction::class.java, loaded.buy[2])
            val requirements = (loaded.buy[3] as RequireAction).requirement!!
            assertEquals(listOf("rich", "vip", "not_banned"), requirements.entries.keys.toList())
            assertEquals(1, requirements.minimum)
            assertTrue(requirements.stopAtSuccess)
            assertEquals(1, requirements.entries.getValue("rich").deny.size)
            assertTrue(requirements.entries.getValue("vip").optional)
            assertEquals(NotCheck(PermissionCheck("shop.banned")), requirements.entries.getValue("not_banned").check)
            assertEquals(listOf(CommandAction("eco take %player_name% 100")), requirements.success)
            assertEquals(1, loaded.gate.entries.size)
        }
    }
}
