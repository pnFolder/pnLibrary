package org.pnfolder.pnlibrary.core.actions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.actions.Action
import org.pnfolder.pnlibrary.api.actions.ChanceCondition
import org.pnfolder.pnlibrary.api.actions.ConditionalAction
import org.pnfolder.pnlibrary.api.actions.DelayAction
import org.pnfolder.pnlibrary.api.actions.MessageAction
import org.pnfolder.pnlibrary.api.actions.PermissionCondition
import org.pnfolder.pnlibrary.api.actions.RepeatAction
import org.pnfolder.pnlibrary.api.actions.RunAction
import org.pnfolder.pnlibrary.api.actions.SequenceAction
import org.pnfolder.pnlibrary.api.config.ConfigOptions
import org.pnfolder.pnlibrary.api.config.yaml
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.config.ConfigurationServiceImpl
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class ActionNodeTest {
    @TempDir
    lateinit var directory: Path

    private fun load(vararg lines: String): List<Action> {
        Files.writeString(directory.resolve("join.yml"), lines.joinToString("\n") + "\n")
        ConfigurationServiceImpl(object : PlatformOwnership {}).use {
            val scope = it.scope(ActionFormatTest.Owner(directory.toFile()), PluginId.of("tester"))
            return scope.yaml<ActionFormatTest.ShopConfig>("join.yml", ConfigOptions.DEFAULT) {
                ActionFormatTest.ShopConfig()
            }.load().value.buy
        }
    }

    @Test
    fun `every node is written with a type and wrappers hold their actions under do`() {
        val actions = load(
            "buy:",
            "  - type: message",
            "    message: Hello, %player_name%",
            "  - type: delay",
            "    duration: 2s",
            "    do:",
            "      - type: message",
            "        message: Later",
            "  - type: chance",
            "    probability: 10%",
            "    do:",
            "      - type: message",
            "        message: Lucky",
            "  - type: if",
            "    check:",
            "      type: permission",
            "      permission: group.vip",
            "    do:",
            "      - type: message",
            "        message: VIP",
            "    else:",
            "      - type: message",
            "        message: Buy VIP",
            "  - type: repeat",
            "    times: 3",
            "    every: 1s",
            "    do:",
            "      - type: message",
            "        message: Tick",
            "  - type: group",
            "    do:",
            "      - type: message",
            "        message: One",
        )

        assertEquals(MessageAction(listOf("Hello, %player_name%")), actions[0])
        assertEquals(DelayAction(Duration.ofSeconds(2), listOf(MessageAction(listOf("Later")))), actions[1])
        assertEquals(
            ConditionalAction(allChecks = listOf(ChanceCondition(0.1)), thenActions = listOf(MessageAction(listOf("Lucky")))),
            actions[2],
        )
        assertEquals(
            ConditionalAction(
                allChecks = listOf(PermissionCondition("group.vip")),
                thenActions = listOf(MessageAction(listOf("VIP"))),
                elseActions = listOf(MessageAction(listOf("Buy VIP"))),
            ),
            actions[3],
        )
        assertEquals(RepeatAction(3, Duration.ofSeconds(1), listOf(MessageAction(listOf("Tick")))), actions[4])
        assertEquals(SequenceAction(listOf(MessageAction(listOf("One")))), actions[5])
    }

    @Test
    fun `wrappers nest and the older forms still read`() {
        val actions = load(
            "buy:",
            "  - \"[message] line\"",
            "  - type: message",
            "    messages: [old]",
            "  - type: delay",
            "    duration: 1s",
            "    do:",
            "      - type: chance",
            "        probability: 50%",
            "        do:",
            "          - type: message",
            "            message: Inner",
        )

        assertEquals(MessageAction(listOf("line")), actions[0])
        assertEquals(MessageAction(listOf("old")), actions[1])
        val delay = actions[2] as DelayAction
        assertInstanceOf(ConditionalAction::class.java, delay.thenActions.single())
    }

    @Test
    fun `a bad percentage is reported at its own entry`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            load("buy:", "  - type: chance", "    probability: 150%", "    do: []")
        }

        assertTrue(error.message!!.contains("at buy[0]") && error.message!!.contains("0% to 100%"), error.message)
    }

    @Test
    fun `run is the template call and group is the sequence`() {
        val actions = load(
            "buy:",
            "  - type: run",
            "    name: refuse",
            "  - type: group",
            "    do:",
            "      - type: message",
            "        message: One",
        )

        assertEquals(RunAction("refuse"), actions[0])
        assertInstanceOf(SequenceAction::class.java, actions[1])
    }

    @Test
    fun `a chance without else and one with else read the same way`() {
        val actions = load(
            "buy:",
            "  - type: chance",
            "    probability: 25%",
            "    do:",
            "      - type: message",
            "        message: Won",
            "    else:",
            "      - type: message",
            "        message: Lost",
        )

        assertEquals(
            ConditionalAction(
                allChecks = listOf(ChanceCondition(0.25)),
                thenActions = listOf(MessageAction(listOf("Won"))),
                elseActions = listOf(MessageAction(listOf("Lost"))),
            ),
            actions.single(),
        )
    }

    @Test
    fun `a repeat needs a sensible number of times`() {
        assertThrows(IllegalArgumentException::class.java) { RepeatAction(times = 0) }
        assertThrows(IllegalArgumentException::class.java) { RepeatAction(times = RepeatAction.MAX_TIMES + 1) }
        assertEquals(1, RepeatAction().times)
    }

    @Test
    fun `a check that is not an object is rejected`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            load("buy:", "  - type: if", "    check: not-an-object-yet", "    do: []")
        }

        assertTrue(error.message!!.contains("at buy[0]"), error.message)
    }
}
