package org.pnfolder.pnlibrary.velocity

import com.velocitypowered.api.command.Command
import com.velocitypowered.api.command.CommandMeta
import com.velocitypowered.api.proxy.ProxyServer
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Answers.RETURNS_DEEP_STUBS
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.definition.command
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandDispatcher
import org.pnfolder.pnlibrary.velocity.commands.VelocityCommandAdapter
import org.pnfolder.pnlibrary.velocity.network.VelocityPlayerAdapter
import org.pnfolder.pnlibrary.velocity.tasks.VelocityTaskAdapter
import java.util.Optional
import java.util.concurrent.CompletableFuture

/** Exercises native metadata and scheduler ownership, beyond the portable dispatcher mocks. */
class VelocityNativeOwnershipTest {
    @Test
    fun `command uses caller owner and unregisters all metadata aliases`() {
        val proxy = mock(ProxyServer::class.java, RETURNS_DEEP_STUBS)
        val owner = Any()
        whenever(proxy.pluginManager.fromInstance(owner)).thenReturn(Optional.of(mock()))
        val adapter = VelocityCommandAdapter(proxy, VelocityPlayerAdapter(proxy))
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
            VelocityCommandAdapter(proxy, VelocityPlayerAdapter(proxy))
                .register(owner, command("owned") { aliases("taken") }, dispatcher)
        }
        org.mockito.kotlin.verify(proxy.commandManager, org.mockito.kotlin.never())
            .register(any<CommandMeta>(), any<Command>())
    }

    @Test
    fun `native task is scheduled under the library plugin`() {
        val proxy = mock(ProxyServer::class.java, RETURNS_DEEP_STUBS)
        val owner = Any()
        whenever(proxy.pluginManager.fromInstance(owner)).thenReturn(Optional.of(mock()))
        val adapter = VelocityTaskAdapter(proxy, owner, {})
        val handle = adapter.buildTask().task {}.execution(TaskExecution.Kind.ASYNC).schedule()
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
            VelocityCommandAdapter(proxy, VelocityPlayerAdapter(proxy))
                .register(owner, command("owned") {}, dispatcher)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VelocityTaskAdapter(proxy, owner, {}).buildTask().task {}.schedule()
        }
    }

    private val dispatcher = object : PlatformCommandDispatcher {
        override fun execute(command: CommandDefinition, context: CommandContext) =
            CompletableFuture.completedFuture<Void>(null)
        override fun suggest(command: CommandDefinition, context: CommandContext) =
            CompletableFuture.completedFuture(emptyList<String>())
    }
}
