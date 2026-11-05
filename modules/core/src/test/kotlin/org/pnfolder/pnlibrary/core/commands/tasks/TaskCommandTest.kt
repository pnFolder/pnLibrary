package org.pnfolder.pnlibrary.core.commands.tasks

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.withSettings
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.TaskId
import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import org.pnfolder.pnlibrary.api.tasks.TaskStatus
import org.pnfolder.pnlibrary.core.commands.CommandRoute
import org.pnfolder.pnlibrary.core.commands.CommandTreeRouter
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeResources
import org.pnfolder.pnlibrary.core.tasks.TaskControl
import java.time.Instant

class TaskCommandTest {
    private val snapshot = TaskSnapshot(
        id = TaskId.random(), name = "cleanup", key = null, ownerName = "pnTest", executionKind = TaskExecution.Kind.GLOBAL,
        status = TaskStatus.SCHEDULED, tags = emptySet(), createdAt = Instant.now(), nextRunAt = Instant.now(),
        runCount = 0, skippedCount = 0, lastStartedAt = null, lastCompletedAt = null, lastFailure = null,
    )
    private val library = mock(PnLibrary::class.java, withSettings().extraInterfaces(RuntimeResources::class.java))
    private val resources = library as RuntimeResources
    private val sent = mutableListOf<String>()
    private val sender = object : CommandSender {
        override val id = "console"
        override val name = "Console"
        override val isConsole = true
        override fun hasPermission(permission: String) = true
        override fun send(message: Component) {
            sent += PlainTextComponentSerializer.plainText().serialize(message)
        }
    }
    private val definition = TaskCommand(library).definition()

    init {
        org.mockito.Mockito.`when`(resources.taskSnapshots()).thenReturn(listOf(snapshot))
    }

    private fun run(vararg words: String): String {
        sent.clear()
        val route = CommandTreeRouter().route(definition, CommandContext(sender, words.toList(), "tasks", words.joinToString(" ")))
        (route as CommandRoute.Executable).node.execution.execute(route.context).toCompletableFuture().join()
        return sent.joinToString("\n")
    }

    @Test
    fun `command is named tasks and has the basic subcommands`() {
        assertEquals("tasks", definition.name)
        val words = definition.root.children.map { it.name }
        assertTrue(words.containsAll(listOf("view", "run", "cancel", "today", "active")), words.toString())
    }

    @Test
    fun `plain command lists the task`() {
        assertTrue(run().contains("cleanup"))
        assertTrue(run("any", "all").contains("cleanup"))
    }

    @Test
    fun `view finds a task by a short id and reports an unknown one`() {
        assertTrue(run("view", snapshot.id.value.take(6)).contains("cleanup"))
        assertTrue(run("view", "zzzzzzzz").contains("Такой задачи нет"))
    }

    @Test
    fun `cancel and run answer by the control result`() {
        val id = snapshot.id.value
        org.mockito.Mockito.`when`(resources.cancelTask(id)).thenReturn(TaskControl.DONE)
        assertTrue(run("cancel", id).contains("остановлена"))
        org.mockito.Mockito.`when`(resources.cancelTask(id)).thenReturn(TaskControl.FORBIDDEN)
        assertTrue(run("cancel", id).contains("не разрешает"))
        org.mockito.Mockito.`when`(resources.runTaskNow(id)).thenReturn(TaskControl.DONE)
        assertTrue(run("run", id).contains("запущена"))
        org.mockito.Mockito.`when`(resources.runTaskNow(id)).thenReturn(TaskControl.UNAVAILABLE)
        assertTrue(run("run", id).contains("нельзя"))
    }
}
