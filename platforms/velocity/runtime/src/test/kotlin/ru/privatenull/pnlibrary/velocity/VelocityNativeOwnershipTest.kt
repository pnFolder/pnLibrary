package ru.privatenull.pnlibrary.velocity

import com.velocitypowered.api.proxy.ProxyServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertThrows
import org.mockito.Answers.RETURNS_DEEP_STUBS
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ru.privatenull.pnlibrary.api.commands.command
import ru.privatenull.pnlibrary.api.tasks.TaskExecution
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandDispatcher
import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskRequest
import ru.privatenull.pnlibrary.velocity.commands.VelocityCommandAdapter
import ru.privatenull.pnlibrary.velocity.tasks.VelocityTaskAdapter
import java.time.Duration
import java.util.Optional
import java.util.concurrent.CompletableFuture

/** Exercises native metadata and scheduler ownership, beyond the portable dispatcher mocks. */
class VelocityNativeOwnershipTest {
    @Test
    fun `command uses caller owner and unregisters all metadata aliases`() {
        val proxy = mock(ProxyServer::class.java, RETURNS_DEEP_STUBS)
        val owner = Any()
        whenever(proxy.pluginManager.fromInstance(owner)).thenReturn(Optional.of(mock()))
        val adapter = VelocityCommandAdapter(proxy, VelocityAudienceAdapter(proxy))
        val registration = adapter.register(owner, command("owned") { aliases("alias") }, dispatcher)

        val builder = proxy.commandManager.metaBuilder("owned").aliases("alias")
        verify(builder).plugin(owner)
        val metadata = builder.plugin(owner).build()
        registration.close()
        verify(proxy.commandManager).unregister(metadata)
    }

    @Test
    fun `existing native command alias cannot be overwritten`() {
        val proxy = mock(ProxyServer::class.java, RETURNS_DEEP_STUBS)
        val owner = Any()
        whenever(proxy.pluginManager.fromInstance(owner)).thenReturn(Optional.of(mock()))
        whenever(proxy.commandManager.hasCommand("taken")).thenReturn(true)
        assertThrows(IllegalArgumentException::class.java) {
            VelocityCommandAdapter(proxy, VelocityAudienceAdapter(proxy))
                .register(owner, command("owned") { aliases("taken") }, dispatcher)
        }
        org.mockito.kotlin.verify(proxy.commandManager, org.mockito.kotlin.never())
            .register(any<com.velocitypowered.api.command.CommandMeta>(), any<com.velocitypowered.api.command.Command>())
    }

    @Test
    fun `native task is scheduled under caller plugin`() {
        val proxy = mock(ProxyServer::class.java, RETURNS_DEEP_STUBS)
        val owner = Any()
        whenever(proxy.pluginManager.fromInstance(owner)).thenReturn(Optional.of(mock()))
        val adapter = VelocityTaskAdapter(proxy)
        val handle = adapter.schedule(PlatformTaskRequest(
            TaskExecution.Kind.ASYNC, null, Duration.ZERO, null, Runnable {}, owner,
        ))
        verify(proxy.scheduler).buildTask(org.mockito.kotlin.eq(owner), any<Runnable>())
        handle.cancel()
        adapter.close()
    }

    @Test
    fun `unregistered plugin cannot own native resources`() {
        val proxy = mock(ProxyServer::class.java, RETURNS_DEEP_STUBS)
        val owner = Any()
        whenever(proxy.pluginManager.fromInstance(owner)).thenReturn(Optional.empty())
        assertThrows(IllegalArgumentException::class.java) {
            VelocityCommandAdapter(proxy, VelocityAudienceAdapter(proxy))
                .register(owner, command("owned") {}, dispatcher)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VelocityTaskAdapter(proxy).schedule(PlatformTaskRequest(
                TaskExecution.Kind.ASYNC, null, Duration.ZERO, null, Runnable {}, owner,
            ))
        }
    }

    private val dispatcher = object : PlatformCommandDispatcher {
        override fun execute(command: CommandDefinition, context: CommandContext) =
            CompletableFuture.completedFuture<Void>(null)
        override fun suggest(command: CommandDefinition, context: CommandContext) =
            CompletableFuture.completedFuture(emptyList<String>())
    }
}
