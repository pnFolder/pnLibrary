package org.pnfolder.pnlibrary.core.actions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.pnfolder.pnlibrary.api.actions.Action
import org.pnfolder.pnlibrary.api.actions.ActionContext
import org.pnfolder.pnlibrary.api.actions.builtin.CommandAction
import org.pnfolder.pnlibrary.api.actions.builtin.CommandRunner
import org.pnfolder.pnlibrary.api.actions.builtin.LogAction
import org.pnfolder.pnlibrary.api.actions.builtin.SoundAction
import org.pnfolder.pnlibrary.api.actions.builtin.TitleAction
import org.pnfolder.pnlibrary.api.audiences.LibraryAudience
import org.pnfolder.pnlibrary.api.audiences.LibraryPlayer
import org.pnfolder.pnlibrary.api.checks.Requirement
import org.pnfolder.pnlibrary.api.checks.builtin.PermissionCheck
import org.pnfolder.pnlibrary.api.config.ConfigOptions
import org.pnfolder.pnlibrary.api.config.yaml
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.text.ComponentService
import org.pnfolder.pnlibrary.config.ConfigurationServiceImpl
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class CheckSystemTest {
    @TempDir
    lateinit var directory: Path

    data class ShopFile(
        var checks: Map<String, Requirement> = emptyMap(),
        var buy: List<Action> = emptyList(),
    )

    private val logger = mock(PnLogger::class.java)

    private fun load(vararg lines: String): ShopFile {
        Files.writeString(directory.resolve("shop.yml"), lines.joinToString("\n") + "\n")
        ConfigurationServiceImpl(object : PlatformOwnership {}).use {
            val scope = it.scope(ActionFormatTest.Owner(directory.toFile()), PluginId.of("tester"))
            return scope.yaml<ShopFile>("shop.yml", ConfigOptions.DEFAULT) { ShopFile() }.load().value
        }
    }

    private fun player(vararg permissions: String): LibraryPlayer = mock(LibraryPlayer::class.java).also { player ->
        `when`(player.name).thenReturn("Alex")
        permissions.forEach { `when`(player.hasPermission(it)).thenReturn(true) }
    }

    private fun context(file: ShopFile, player: LibraryPlayer, money: Int = 0) = ActionContext(
        player = player,
        allPlayers = mock(LibraryAudience::class.java),
        components = mock(ComponentService::class.java),
        logger = logger,
        tasks = mock(TaskScope::class.java),
        values = mapOf("money" to money),
        checks = file.checks,
    )

    /** Texts written by `type: log` actions, in order. */
    private fun logged(): List<String> =
        mockingDetails(logger).invocations.filter { it.method.name == "info" }.map { it.arguments[0].toString() }

    private fun warnings(): List<String> =
        mockingDetails(logger).invocations.filter { it.method.name == "warning" }.map { it.arguments[0].toString() }

    private val shop = arrayOf(
        "checks:",
        "  has-100:",
        "    check:",
        "      type: '>='",
        "      input: money",
        "      output: 100",
        "    deny:",
        "      - type: log",
        "        text: no money",
        "    success:",
        "      - type: log",
        "        text: paid",
        "  vip:",
        "    check:",
        "      type: permission",
        "      permission: group.vip",
        "buy:",
        "  - type: require",
        "    check: has-100",
        "  - type: log",
        "    text: bought",
        "  - type: if",
        "    check: vip",
        "    do:",
        "      - type: log",
        "        text: vip bonus",
        "    else:",
        "      - type: log",
        "        text: no bonus",
    )

    @Test
    fun `a named check passes, runs its success and the chain goes on`() {
        val file = load(*shop)
        val ctx = context(file, player("group.vip"), money = 150)

        ctx.execute(file.buy)

        assertEquals(listOf("paid", "bought", "vip bonus"), logged())
        assertFalse(ctx.stopped)
    }

    @Test
    fun `a failed require runs the check's deny written with type and stops`() {
        val file = load(*shop)
        val ctx = context(file, player(), money = 40)

        ctx.execute(file.buy)

        assertEquals(listOf("no money"), logged())
        assertTrue(ctx.stopped)
    }

    @Test
    fun `if only asks a named check and keeps going`() {
        val file = load(*shop)

        context(file, player(), money = 150).execute(file.buy)

        assertEquals(listOf("paid", "bought", "no bonus"), logged())
    }

    @Test
    fun `checks work on their own from code`() {
        val file = load(*shop)
        val rich = context(file, player(), money = 500)
        val poor = context(file, player(), money = 5)

        assertTrue(file.checks.getValue("has-100").check(rich))
        assertFalse(file.checks.getValue("has-100").check(poor))
        assertEquals(emptyList<String>(), logged())
        assertFalse(file.checks.getValue("has-100").test(poor))
        assertEquals(listOf("no money"), logged())
    }

    @Test
    fun `all, any, not and at-least combine checks`() {
        val file = load(
            "checks:",
            "  staff:",
            "    check:",
            "      type: any",
            "      checks:",
            "        - type: permission",
            "          permission: group.admin",
            "        - type: permission",
            "          permission: group.mod",
            "  trader:",
            "    check:",
            "      type: all",
            "      checks:",
            "        - type: permission",
            "          permission: shop.use",
            "        - type: not",
            "          check:",
            "            type: permission",
            "            permission: shop.banned",
            "  veteran:",
            "    check:",
            "      type: at-least",
            "      count: 2",
            "      checks:",
            "        - staff",
            "        - trader",
            "        - type: '>='",
            "          input: money",
            "          output: 10",
        )
        val checks = file.checks

        assertTrue(checks.getValue("staff").check(context(file, player("group.mod"))))
        assertFalse(checks.getValue("staff").check(context(file, player())))
        assertTrue(checks.getValue("trader").check(context(file, player("shop.use"))))
        assertFalse(checks.getValue("trader").check(context(file, player("shop.use", "shop.banned"))))
        assertTrue(checks.getValue("veteran").check(context(file, player("shop.use"), money = 20)))
        assertFalse(checks.getValue("veteran").check(context(file, player("shop.use"))))
    }

    @Test
    fun `an unknown check name warns with a suggestion`() {
        val file = load(*shop, "  - type: require", "    check: has-10")

        context(file, player("group.vip"), money = 150).execute(file.buy)

        assertTrue(warnings().any { it.contains("Unknown check 'has-10'") && it.contains("has-100") }, warnings().toString())
    }

    @Test
    fun `sound and title read the friendly names`() {
        val file = load(
            "buy:",
            "  - type: sound",
            "    sound: ENTITY_VILLAGER_NO",
            "  - type: title",
            "    title: Hi",
            "    fade-in: 1s",
            "    stay: 3s",
            "    fade-out: 2s",
        )

        assertEquals(SoundAction("entity.villager.no"), file.buy[0])
        assertEquals(
            TitleAction("Hi", fadeIn = Duration.ofSeconds(1), stay = Duration.ofSeconds(3), fadeOut = Duration.ofSeconds(2)),
            file.buy[1],
        )
    }

    @Test
    fun `a line of text in deny is rejected`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            load("checks:", "  a:", "    check: b", "    deny:", "      - '[message] no'")
        }

        assertTrue(error.message!!.contains("checks.a.deny[0]") && error.message!!.contains("type: message"), error.message)
    }

    @Test
    fun `default checks are written with type and read back the same`() {
        val defaults = ShopFile(
            checks = mapOf(
                "vip" to Requirement(
                    PermissionCheck("group.vip"),
                    deny = listOf(LogAction("no vip")),
                    success = listOf(CommandAction("say hi", CommandRunner.CONSOLE)),
                ),
            ),
        )
        ConfigurationServiceImpl(object : PlatformOwnership {}).use {
            val scope = it.scope(ActionFormatTest.Owner(directory.toFile()), PluginId.of("tester"))
            scope.yaml<ShopFile>("written.yml", ConfigOptions.DEFAULT) { defaults }.load()
        }
        val text = Files.readString(directory.resolve("written.yml"))
        assertTrue(text.contains("type: log") && !text.contains("[log]"), text)

        Files.copy(directory.resolve("written.yml"), directory.resolve("shop.yml"))
        assertEquals(defaults.checks, load(*Files.readAllLines(directory.resolve("written.yml")).toTypedArray()).checks)
    }

    @Test
    fun `console and player types run commands`() {
        val file = load(
            "buy:",
            "  - type: console",
            "    command: give %player_name% apple",
            "  - type: player",
            "    command: spawn",
        )

        assertEquals(
            listOf(CommandAction("give %player_name% apple", CommandRunner.CONSOLE), CommandAction("spawn", CommandRunner.PLAYER)),
            file.buy,
        )
    }

    @Test
    fun `a chance probability over 100 percent is rejected at its entry`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            load("buy:", "  - type: chance", "    probability: 150%", "    do: []")
        }

        assertTrue(error.message!!.contains("buy[0]") && error.message!!.contains("0% to 100%"), error.message)
    }
}
