package org.pnfolder.pnlibrary.velocity;

import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandDispatcher;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandRegistration;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle;
import org.pnfolder.pnlibrary.velocity.commands.VelocityCommandAdapter;
import org.pnfolder.pnlibrary.velocity.network.VelocityAudienceAdapter;
import org.pnfolder.pnlibrary.velocity.tasks.VelocityTaskAdapter;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition.command;

/** Exercises native metadata and scheduler ownership, beyond the portable dispatcher mocks. */
class VelocityNativeOwnershipTest {
    private final PlatformCommandDispatcher dispatcher = new PlatformCommandDispatcher() {
        @NotNull
        @Override
        public CompletionStage<Void> execute(@NotNull CommandDefinition command, @NotNull CommandContext context) {
            return CompletableFuture.completedFuture(null);
        }

        @NotNull
        @Override
        public CompletionStage<List<String>> suggest(@NotNull CommandDefinition command,
                                                     @NotNull CommandContext context) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
    };

    private static ProxyServer proxyOwnedBy(Object owner, boolean registered) {
        ProxyServer proxy = mock(ProxyServer.class, RETURNS_DEEP_STUBS);
        Optional<PluginContainer> container = registered
                ? Optional.of(mock(PluginContainer.class)) : Optional.empty();
        when(proxy.getPluginManager().fromInstance(owner)).thenReturn(container);
        return proxy;
    }

    @Test
    void commandUsesCallerOwnerAndUnregistersAllMetadataAliases() {
        Object owner = new Object();
        ProxyServer proxy = proxyOwnedBy(owner, true);
        VelocityCommandAdapter adapter = new VelocityCommandAdapter(proxy, new VelocityAudienceAdapter(proxy));

        PlatformCommandRegistration registration = adapter.register(owner,
                command("owned", builder -> builder.aliases("alias")), dispatcher);

        CommandMeta.Builder builder = proxy.getCommandManager().metaBuilder("owned").aliases("alias");
        verify(builder).plugin(owner);
        CommandMeta metadata = builder.plugin(owner).build();
        registration.close();
        verify(proxy.getCommandManager()).unregister(metadata);
    }

    @Test
    void existingNativeCommandAliasCannotBeOverwritten() {
        Object owner = new Object();
        ProxyServer proxy = proxyOwnedBy(owner, true);
        when(proxy.getCommandManager().hasCommand("taken")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                new VelocityCommandAdapter(proxy, new VelocityAudienceAdapter(proxy))
                        .register(owner, command("owned", builder -> builder.aliases("taken")), dispatcher));

        verify(proxy.getCommandManager(), never()).register(any(CommandMeta.class), any(Command.class));
    }

    @Test
    void nativeTaskIsScheduledUnderTheLibraryPlugin() {
        Object owner = new Object();
        ProxyServer proxy = proxyOwnedBy(owner, true);
        VelocityTaskAdapter adapter = new VelocityTaskAdapter(proxy, owner, message -> { });

        PlatformTaskHandle handle = adapter.buildTask().task(() -> { }).execution(TaskExecution.Kind.ASYNC)
                .schedule();

        verify(proxy.getScheduler()).buildTask(eq(owner), any(Runnable.class));
        handle.cancel();
        adapter.close();
    }

    @Test
    void unregisteredPluginCannotOwnNativeResources() {
        Object owner = new Object();
        ProxyServer proxy = proxyOwnedBy(owner, false);

        assertThrows(IllegalArgumentException.class, () ->
                new VelocityCommandAdapter(proxy, new VelocityAudienceAdapter(proxy))
                        .register(owner, command("owned", builder -> { }), dispatcher));
        assertThrows(IllegalArgumentException.class, () ->
                new VelocityTaskAdapter(proxy, owner, message -> { }).buildTask().task(() -> { }).schedule());
    }
}
