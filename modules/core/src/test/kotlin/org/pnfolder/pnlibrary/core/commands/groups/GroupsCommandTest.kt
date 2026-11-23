package org.pnfolder.pnlibrary.core.commands.groups

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.core.commands.engine.CommandRoute
import org.pnfolder.pnlibrary.core.commands.engine.CommandTreeRouter
import org.pnfolder.pnlibrary.core.link.Link
import java.nio.file.Path

class GroupsCommandTest {
    @TempDir lateinit var directory: Path

    private val link by lazy { Link(null, directory) }
    private val definition by lazy { GroupsCommand(link) { emptyList() }.definition() }
    private val sent = mutableListOf<String>()
    private val sender = object : CommandSender {
        override fun pointers() = org.pnfolder.pnlibrary.api.sender.senderPointers("Console") { permission -> true }
        override fun send(message: Component) {
            sent += PlainTextComponentSerializer.plainText().serialize(message)
        }
    }

    private fun run(vararg words: String): String {
        sent.clear()
        val route = CommandTreeRouter().route(definition, CommandContext(sender, words.toList(), "groups", words.joinToString(" ")))
        (route as CommandRoute.Executable).node.execution.execute(route.context).toCompletableFuture().join()
        return sent.joinToString("\n")
    }

    @Test
    fun `a group is created once and a bad name is refused`() {
        assertTrue(run("create", "grief").contains("создана"))
        assertTrue(run("create", "grief").contains("уже есть"))
        assertTrue(run("create", "x".repeat(40)).contains("Не подходит имя"))
        assertNotNull(link.groups().get("GRIEF"))
    }

    @Test
    fun `join, require and a per-server difference are saved in the group`() {
        run("create", "grief")

        run("join", "grief", "grief-1")
        run("require", "grief", "Shop")
        run("skip", "grief", "grief-1", "Shop")
        run("extra", "grief", "grief-1", "Quests")
        val group = link.groups().get("grief")!!

        assertEquals(setOf("grief-1"), group.servers)
        assertEquals(setOf("Shop"), group.plugins)
        assertEquals(setOf("Quests"), group.wanted("grief-1"))
        assertTrue(group.customized("grief-1"))
    }

    @Test
    fun `reset returns a server to what the rest have and leave takes it out`() {
        run("create", "grief")
        run("join", "grief", "grief-1")
        run("require", "grief", "Shop")
        run("skip", "grief", "grief-1", "Shop")

        run("reset", "grief", "grief-1")
        assertEquals(setOf("Shop"), link.groups().get("grief")!!.wanted("grief-1"))

        run("leave", "grief", "grief-1")
        assertTrue(link.groups().get("grief")!!.servers.isEmpty())
    }

    @Test
    fun `drop removes a plugin from the group`() {
        run("create", "grief")
        run("require", "grief", "Shop")

        run("drop", "grief", "shop")

        assertTrue(link.groups().get("grief")!!.plugins.isEmpty())
    }

    @Test
    fun `delete removes the group and an unknown group is reported`() {
        run("create", "grief")

        assertTrue(run("delete", "grief").contains("удалена"))
        assertNull(link.groups().get("grief"))
        assertTrue(run("delete", "grief").contains("Нет группы"))
        assertTrue(run("join", "ghost", "s1").contains("Нет группы"))
    }

    @Test
    fun `info on an unknown group does not crash`() {
        assertTrue(run("info", "ghost").isNotBlank())
    }
}
