package org.pnfolder.pnlibrary.bukkit.commands;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.core.commands.engine.TabSuggestions;
import org.pnfolder.pnlibrary.spi.commands.NativeCommandExecutor;
import org.pnfolder.pnlibrary.spi.commands.NativeCommandRegistrar;
import org.pnfolder.pnlibrary.spi.commands.NativeCommandSuggester;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandRegistration;

/** The classic registration: a command declared in {@code plugin.yml}, or one put into the server's command map. */
final class DefaultBukkitNativeCommandRegistrar implements NativeCommandRegistrar<CommandSender> {
    @NotNull
    @Override
    public PlatformCommandRegistration register(@NotNull Object owner, @NotNull CommandDefinition command,
                                                @NotNull NativeCommandExecutor<CommandSender> execute,
                                                @NotNull NativeCommandSuggester<CommandSender> suggest) {
        if (!(owner instanceof Plugin)) {
            throw new IllegalArgumentException("Command owner must be a Bukkit plugin");
        }
        Plugin ownerPlugin = (Plugin) owner;
        CommandExecutor executor = (sender, ignored, label, args) -> {
            execute.execute(sender, label, args);
            return true;
        };
        TabCompleter completer = (sender, ignored, label, args) ->
            TabSuggestions.suggestionsWithin(suggest.suggest(sender, label, args),
                TabSuggestions.MAIN_THREAD_SUGGESTION_MILLIS);
        PluginCommand declared = ownerPlugin instanceof JavaPlugin ? ((JavaPlugin) ownerPlugin).getCommand(command.getName()) : null;
        if (declared != null) {
            declared.setAliases(new ArrayList<>(command.getAliases()));
            declared.setExecutor(executor);
            declared.setTabCompleter(completer);
            return () -> {
                declared.setExecutor(null);
                declared.setTabCompleter(null);
            };
        }
        CommandMap commandMap = commandMap();
        PluginCommand created = pluginCommand(command.getName(), ownerPlugin);
        created.setAliases(new ArrayList<>(command.getAliases()));
        created.setDescription("pnLibrary portable command");
        created.setExecutor(executor);
        created.setTabCompleter(completer);
        commandMap.register(ownerPlugin.getName(), created);
        return () -> created.unregister(commandMap);
    }

    private static CommandMap commandMap() {
        try {
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            return (CommandMap) field.get(Bukkit.getServer());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static PluginCommand pluginCommand(String name, Plugin owner) {
        try {
            Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);
            return constructor.newInstance(name, owner);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }
}
