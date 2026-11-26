package org.pnfolder.pnlibrary.velocity.commands;

import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.spi.commands.NativeCommandExecutor;
import org.pnfolder.pnlibrary.spi.commands.NativeCommandRegistrar;
import org.pnfolder.pnlibrary.spi.commands.NativeCommandSuggester;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Registers a command with the Velocity command manager on behalf of a registered plugin. */
final class VelocityNativeCommandRegistrar implements NativeCommandRegistrar<CommandSource> {
    private final ProxyServer server;

    VelocityNativeCommandRegistrar(ProxyServer server) {
        this.server = server;
    }

    @NotNull
    @Override
    public PlatformCommandRegistration register(
            @NotNull Object owner,
            @NotNull CommandDefinition command,
            @NotNull NativeCommandExecutor<CommandSource> execute,
            @NotNull NativeCommandSuggester<CommandSource> suggest) {
        if (!server.getPluginManager().fromInstance(owner).isPresent()) {
            throw new IllegalArgumentException("Command owner must be a registered Velocity plugin");
        }
        requireFree(command);
        CommandMeta metadata = server.getCommandManager().metaBuilder(command.getName())
                .aliases(command.getAliases().toArray(new String[0]))
                .plugin(owner)
                .build();
        server.getCommandManager().register(metadata, new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                execute.execute(invocation.source(), invocation.alias(), invocation.arguments());
            }

            @Override
            public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
                return suggest.suggest(invocation.source(), invocation.alias(), invocation.arguments())
                        .toCompletableFuture();
            }
        });
        return () -> server.getCommandManager().unregister(metadata);
    }

    private void requireFree(CommandDefinition command) {
        List<String> names = new ArrayList<>();
        names.add(command.getName());
        names.addAll(command.getAliases());
        for (String name : names) {
            if (server.getCommandManager().hasCommand(name)) {
                throw new IllegalArgumentException(
                        "Command name or alias '" + name + "' is already registered on Velocity");
            }
        }
    }
}
