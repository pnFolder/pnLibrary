package org.pnfolder.pnlibrary.core.actions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.pnfolder.pnlibrary.api.actions.Action
import org.pnfolder.pnlibrary.api.actions.ActionContext
import org.pnfolder.pnlibrary.api.actions.ActionLine
import org.pnfolder.pnlibrary.api.actions.ActionTarget
import org.pnfolder.pnlibrary.api.actions.CommandAction
import org.pnfolder.pnlibrary.api.actions.CommandRunner
import org.pnfolder.pnlibrary.api.actions.ChanceCondition
import org.pnfolder.pnlibrary.api.actions.Comparison
import org.pnfolder.pnlibrary.api.actions.ConditionSerializer
import org.pnfolder.pnlibrary.api.actions.ConditionalAction
import org.pnfolder.pnlibrary.api.actions.DelayAction
import org.pnfolder.pnlibrary.api.actions.ExperienceCondition
import org.pnfolder.pnlibrary.api.actions.ItemCondition
import org.pnfolder.pnlibrary.api.actions.LibraryAudience
import org.pnfolder.pnlibrary.api.actions.LibraryPlayer
import org.pnfolder.pnlibrary.api.actions.LengthCondition
import org.pnfolder.pnlibrary.api.actions.MessageAction
import org.pnfolder.pnlibrary.api.actions.NotCondition
import org.pnfolder.pnlibrary.api.actions.PermissionCondition
import org.pnfolder.pnlibrary.api.actions.PermissionsCondition
import org.pnfolder.pnlibrary.api.actions.RegexCondition
import org.pnfolder.pnlibrary.api.actions.RequirementSerializer
import org.pnfolder.pnlibrary.api.actions.RequirementsAction
import org.pnfolder.pnlibrary.api.actions.RunAction
import org.pnfolder.pnlibrary.api.actions.SoundAction
import org.pnfolder.pnlibrary.api.actions.WaitAction
import org.pnfolder.pnlibrary.api.actions.TitleAction
import org.pnfolder.pnlibrary.api.actions.StopAction
import org.pnfolder.pnlibrary.api.actions.TypeCondition
import org.pnfolder.pnlibrary.api.actions.ValueCondition
import org.pnfolder.pnlibrary.api.actions.ValueType
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

    // ---- action lines ----

    @Test
    fun `tags build the matching actions`() {
        assertEquals(MessageAction(listOf("&aHi %player_name%")), ActionLine.parse("[message] &aHi %player_name%"))
        assertEquals(
            MessageAction(listOf("Hi"), target = ActionTarget.ALL_PLAYERS),
            ActionLine.parse("[broadcast] Hi"),
        )
        assertEquals(CommandAction("give %player_name% apple", CommandRunner.CONSOLE), ActionLine.parse("[console] give %player_name% apple"))
        assertEquals(CommandAction("spawn", CommandRunner.PLAYER), ActionLine.parse("[player] spawn"))
        assertEquals(CommandAction("spawn", CommandRunner.PLAYER), ActionLine.parse("[commandevent] spawn"))
        assertInstanceOf(StopAction::class.java, ActionLine.parse("[stop]"))
    }

    @Test
    fun `tags are not case sensitive`() {
        assertEquals(ActionLine.parse("[message] Hi"), ActionLine.parse("[MESSAGE] Hi"))
    }

    @Test
    fun `a sound takes a name, volume and pitch`() {
        val sound = ActionLine.parse("[sound] ENTITY_VILLAGER_NO 0.5 2") as SoundAction

        assertEquals("entity.villager.no", sound.key)
        assertEquals(0.5f, sound.volume)
        assertEquals(2f, sound.pitch)
        assertEquals("minecraft:block.note_block.pling", (ActionLine.parse("[sound] minecraft:block.note_block.pling") as SoundAction).key)
    }

    @Test
    fun `delay waits ticks or seconds before the action`() {
        val ticks = ActionLine.parse("[message] Hi<delay=20>") as DelayAction
        val seconds = ActionLine.parse("[message] Hi <delay=2s>") as DelayAction

        assertEquals(Duration.ofSeconds(1), ticks.duration)
        assertEquals(Duration.ofSeconds(2), seconds.duration)
        assertEquals(listOf(MessageAction(listOf("Hi"))), ticks.thenActions)
    }

    @Test
    fun `chance wraps the action and delay and chance can be combined`() {
        val chance = ActionLine.parse("[message] Hi<chance=25>") as ConditionalAction
        assertEquals(listOf(ChanceCondition(0.25)), chance.allChecks)

        val both = ActionLine.parse("[message] Hi<delay=40><chance=50>") as ConditionalAction
        assertEquals(ChanceCondition(0.5), both.allChecks.single())
        assertInstanceOf(DelayAction::class.java, both.thenActions.single())
    }

    @Test
    fun `invalid lines are rejected with a clear reason`() {
        assertTrue(assertThrows(IllegalArgumentException::class.java) { ActionLine.parse("[teleport] 1 2 3") }.message!!.startsWith("Unknown action tag [teleport]."))
        assertTrue(assertThrows(IllegalArgumentException::class.java) { ActionLine.parse("Hello") }.message!!.contains("[tag]"))
        assertThrows(IllegalArgumentException::class.java) { ActionLine.parse("[message] Hi<chance=150>") }
        assertThrows(IllegalArgumentException::class.java) { ActionLine.parse("[message] Hi<delay=soon>") }
    }

    @Test
    fun `lines written back read the same`() {
        listOf("[message] Hello", "[broadcast] Hello", "[console] say hi", "[player] spawn", "[actionbar] Hi", "[stop]", "[log] note")
            .forEach { line -> assertEquals(line, ActionLine.format(ActionLine.parse(line)), line) }
    }

    // ---- conditions ----

    private fun condition(vararg pairs: Pair<String, Any?>) = ConditionSerializer.parse(mapOf(*pairs))

    @Test
    fun `permission types read their fields`() {
        assertEquals(PermissionCondition("shop.use"), condition("type" to "has permission", "permission" to "shop.use"))
        assertEquals(
            PermissionsCondition(listOf("a", "b"), 1),
            condition("type" to "has permissions", "permissions" to listOf("a", "b"), "minimum" to 1),
        )
    }

    @Test
    fun `a bang negates and the spelling of keys does not matter`() {
        val negated = condition("type" to "!has permission", "permission" to "shop.use")
        assertEquals(NotCondition(PermissionCondition("shop.use")), negated)

        val item = condition("type" to "has-item", "material" to "diamond", "amount" to 3, "name-contains" to "Key", "loreContains" to "Rare")
        assertEquals(ItemCondition("diamond", 3, nameContains = "Key", loreContains = "Rare", ignoreCase = false), item)
    }

    @Test
    fun `string types and comparators read input and output`() {
        assertEquals(
            ValueCondition("%player_world%", Comparison.EQUALS, "world", ignoreCase = false),
            condition("type" to "string equals", "input" to "%player_world%", "output" to "world"),
        )
        assertEquals(
            ValueCondition("a", Comparison.EQUALS, "b", ignoreCase = true),
            condition("type" to "string equals ignorecase", "input" to "a", "output" to "b"),
        )
        assertEquals(
            ValueCondition("%vault_eco_balance%", Comparison.GREATER_OR_EQUAL, "100"),
            condition("type" to ">=", "input" to "%vault_eco_balance%", "output" to 100),
        )
        assertEquals(ValueCondition("x", Comparison.NUMBER_EQUALS, "5"), condition("type" to "==", "input" to "x", "output" to 5))
    }

    @Test
    fun `other types read their fields`() {
        assertEquals(ExperienceCondition(30, true), condition("type" to "has exp", "amount" to 30, "level" to true))
        assertEquals(LengthCondition("x", 2, 8), condition("type" to "string length", "input" to "x", "min" to 2, "max" to 8))
        assertEquals(TypeCondition("x", ValueType.UUID), condition("type" to "is object", "input" to "x", "object" to "uuid"))
        assertEquals(RegexCondition("x", "[a-z]+"), condition("type" to "regex matches", "input" to "x", "regex" to "[a-z]+"))
    }

    @Test
    fun `unknown types and missing fields fail with a clear reason`() {
        assertTrue(assertThrows(IllegalArgumentException::class.java) { condition("type" to "has wings") }.message!!.contains("has wings"))
        assertTrue(assertThrows(IllegalArgumentException::class.java) { condition("type" to "has permission") }.message!!.contains("permission"))
        assertThrows(IllegalArgumentException::class.java) { condition("permission" to "x") }
    }

    @Test
    fun `a mistyped tag suggests the closest one`() {
        val message = assertThrows(IllegalArgumentException::class.java) { ActionLine.parse("[mesage] hi") }.message!!

        assertTrue(message.contains("Did you mean [message]?"), message)
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
            PermissionCondition("a.b"),
            PermissionsCondition(listOf("a", "b"), 2),
            ExperienceCondition(5, true),
            LengthCondition("x", 1, 9),
            ValueCondition("x", Comparison.GREATER_THAN, "5"),
            ValueCondition("x", Comparison.EQUALS, "y", ignoreCase = false),
            NotCondition(PermissionCondition("a")),
        ).forEach { original ->
            val written = ConditionSerializer.describe(original)!!
            assertEquals(original, ConditionSerializer.parse(written), written.toString())
        }
    }

    // ---- a real YAML file ----

    data class ShopConfig(
        var buy: List<Action> = emptyList(),
        var gate: RequirementsAction = RequirementsAction(),
    )

    class Owner(private val folder: File) {
        fun getDataFolder(): File = folder
        fun getLogger(): Logger = Logger.getLogger("action-format-test")
    }

    @Test
    fun `a bad line is reported at its own entry with the reason`() {
        Files.writeString(
            directory.resolve("shop.yml"),
            listOf("buy:", "  - \"[message] fine\"", "  - \"[mesage] oops\"").joinToString("\n") + "\n",
        )

        val service = ConfigurationServiceImpl(object : PlatformOwnership {})
        service.use {
            val scope = it.scope(Owner(directory.toFile()), PluginId.of("tester"))
            val error = assertThrows(IllegalArgumentException::class.java) {
                scope.yaml<ShopConfig>("shop.yml", ConfigOptions.DEFAULT) { ShopConfig() }.load()
            }

            val message = error.message!!
            assertTrue(message.contains("at buy[1]"), message)
            assertTrue(message.contains("Did you mean [message]?"), message)
            assertTrue(!message.contains("[message] fine"), message)
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
                "          - \"[message] no\"",
            ).joinToString("\n") + "\n",
        )

        val service = ConfigurationServiceImpl(object : PlatformOwnership {})
        service.use {
            val scope = it.scope(Owner(directory.toFile()), PluginId.of("tester"))
            val error = assertThrows(IllegalArgumentException::class.java) {
                scope.yaml<ShopConfig>("shop.yml", ConfigOptions.DEFAULT) { ShopConfig() }.load()
            }

            val message = error.message!!
            assertTrue(message.contains("at buy[0].requirements.rich"), message)
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
                "    - \"[log] no\"",
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

            gate.execute(context)

            val warnings = mockingDetails(logger).invocations.filter { call -> call.method.name == "warning" }
            assertTrue(warnings.any { call -> call.arguments[0].toString().contains("can never pass") }, warnings.toString())
            assertTrue(context.stopped)
        }
    }

    @Test
    fun `a yaml file with titles, waits, templates and short requirements loads`() {
        Files.writeString(
            directory.resolve("shop.yml"),
            listOf(
                "buy:",
                "  - \"[title] &aWelcome;&7Hi;10;70;20\"",
                "  - \"[wait 2s]\"",
                "  - \"[run] refuse\"",
                "  - type: requirements",
                "    requirements:",
                "      rich:",
                "        if: \"%vault_eco_balance% >= 100\"",
                "        deny: \"&cNeed 100\"",
                "      sober: \"%drunk% == 0\"",
            ).joinToString("\n") + "\n",
        )

        val service = ConfigurationServiceImpl(object : PlatformOwnership {})
        service.use {
            val scope = it.scope(Owner(directory.toFile()), PluginId.of("tester"))
            val loaded = scope.yaml<ShopConfig>("shop.yml", ConfigOptions.DEFAULT) { ShopConfig() }.load().value

            assertInstanceOf(TitleAction::class.java, loaded.buy[0])
            assertEquals(WaitAction(Duration.ofSeconds(2)), loaded.buy[1])
            assertEquals(RunAction("refuse"), loaded.buy[2])
            val requirements = loaded.buy[3] as RequirementsAction
            assertEquals(listOf("rich", "sober"), requirements.requirements.keys.toList())
            assertEquals(1, requirements.requirements.getValue("rich").deny.size)
            assertEquals(
                ValueCondition("%drunk%", Comparison.NUMBER_EQUALS, "0"),
                requirements.requirements.getValue("sober").check,
            )
        }
    }

    @Test
    fun `a yaml file written in the menu style loads`() {
        Files.writeString(
            directory.resolve("shop.yml"),
            """
            buy:
              - "[message] &aHello %player_name%"
              - "[sound] ENTITY_PLAYER_LEVELUP 1 1"
              - "[console] give %player_name% diamond 1<delay=20>"
              - type: requirements
                requirements:
                  rich:
                    type: ">="
                    input: "%vault_eco_balance%"
                    output: 100
                    deny_commands:
                      - "[message] &cNot enough"
                  vip:
                    type: has permission
                    permission: shop.vip
                    optional: true
                    success_commands:
                      - "[message] VIP discount"
                  not_banned:
                    type: "!has permission"
                    permission: shop.banned
                minimum_requirements: 1
                stop_at_success: true
                deny_commands:
                  - "[sound] ENTITY_VILLAGER_NO"
                success_commands:
                  - "[console] eco take %player_name% 100"
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
            val requirements = loaded.buy[3] as RequirementsAction
            assertEquals(listOf("rich", "vip", "not_banned"), requirements.requirements.keys.toList())
            assertEquals(1, requirements.minimum)
            assertTrue(requirements.stopAtSuccess)
            assertEquals(1, requirements.requirements.getValue("rich").deny.size)
            assertTrue(requirements.requirements.getValue("vip").optional)
            assertEquals(NotCondition(PermissionCondition("shop.banned")), requirements.requirements.getValue("not_banned").check)
            assertEquals(listOf(CommandAction("eco take %player_name% 100")), requirements.success)
            assertEquals(1, loaded.gate.requirements.size)
        }
    }
}
