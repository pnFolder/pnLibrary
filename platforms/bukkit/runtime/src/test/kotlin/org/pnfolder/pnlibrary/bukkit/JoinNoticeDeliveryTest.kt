package org.pnfolder.pnlibrary.bukkit

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.stubbing.Answer
import org.pnfolder.pnlibrary.api.tasks.TaskContext
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.TaskHandle
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.tasks.TaskSpec
import java.util.concurrent.atomic.AtomicReference

class JoinNoticeDeliveryTest {
    private val specs = mutableListOf<TaskSpec>()
    private val scope: TaskScope = mock(TaskScope::class.java, Answer { call ->
        if (call.method.name != "schedule") return@Answer null
        specs += call.arguments[0] as TaskSpec
        mock(TaskHandle::class.java)
    })
    private val player: Player = mock(Player::class.java).also { `when`(it.isOnline).thenReturn(true) }
    private val context: TaskContext = mock(TaskContext::class.java)

    @Test
    fun `the journal is read by the asynchronous task and only the send runs on the player thread`() {
        val builtOn = AtomicReference<Thread>()
        val sentOn = AtomicReference<Thread>()
        val sent = mutableListOf<Component>()
        val lines = listOf(Component.text("one"), Component.text("two"))
        val main = Thread.currentThread()

        JoinNoticeDelivery(scope).deliver(player, build = { builtOn.set(Thread.currentThread()); lines }) { text ->
            sentOn.set(Thread.currentThread())
            sent += text
        }

        assertEquals(1, specs.size)
        assertEquals(TaskExecution.Kind.ASYNC, specs[0].execution.kind)
        assertEquals(null, builtOn.get(), "nothing is read while the join event is handled")

        val worker = Thread { specs[0].action.run(context) }.also { it.start(); it.join() }
        assertNotEquals(main, builtOn.get())
        assertEquals(worker, builtOn.get())
        assertEquals(2, specs.size)
        assertEquals(TaskExecution.Kind.ENTITY, specs[1].execution.kind)
        assertEquals(null, sentOn.get())

        specs[1].action.run(context)
        assertEquals(main, sentOn.get())
        assertEquals(lines, sent)
    }

    @Test
    fun `an empty notice schedules no send`() {
        JoinNoticeDelivery(scope).deliver(player, build = { emptyList() }) { error("must not send") }

        specs[0].action.run(context)

        assertTrue(specs.size == 1)
    }
}
