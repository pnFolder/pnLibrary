package org.pnfolder.pnlibrary.bungee.commands;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.spi.commands.NativeCommandExecutor;
import org.pnfolder.pnlibrary.spi.commands.NativeCommandRegistrar;
import org.pnfolder.pnlibrary.spi.commands.NativeCommandSuggester;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandRegistration;

/** Registers a command with the Bungee plugin manager of the owning plugin. */
final class BungeeNativeCommandRegistrar implements NativeCommandRegistrar<CommandSender> {

    @NotNull
    @Override
    public PlatformCommandRegistration register(
            @NotNull Object owner,
            @NotNull CommandDefinition command,
            @NotNull NativeCommandExecutor<CommandSender> execute,
            @NotNull NativeCommandSuggester<CommandSender> suggest) {
        if (!(owner instanceof Plugin)) {
            throw new IllegalArgumentException("Command owner must be a Bungee plugin");
        }
        Plugin ownerPlugin = (Plugin) owner;
        BungeeNativeCommand nativeCommand = new BungeeNativeCommand(command, execute, suggest);
        ownerPlugin.getProxy().getPluginManager().registerCommand(ownerPlugin, nativeCommand);
        return () -> ownerPlugin.getProxy().getPluginManager().unregisterCommand(nativeCommand);
    }
}
