package org.pnfolder.pnlibrary.bukkit.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.spi.commands.NativeCommandRegistrar;
import org.pnfolder.pnlibrary.spi.commands.NativeCommandExecutor;
import org.pnfolder.pnlibrary.spi.commands.NativeCommandSuggester;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandRegistration;

// Paper builds its command tree on plugin load and on datapack reload; commands can only be added then.
// Everything registered before that event is queued here and submitted in bulk; anything later goes through
// the fallback (classic command map) and still works, only without client-side validation.
// Registering a lifecycle handler also disables Bukkit's /reload; that is Paper's rule, not ours.
@SuppressWarnings("UnstableApiUsage")
public final class PaperNativeCommandRegistrar implements NativeCommandRegistrar<CommandSender> {
    private final Plugin plugin;
    private final NativeCommandRegistrar<CommandSender> fallback;
    private final Supplier<PnLogger> pnLogger;
    private final List<PaperCommandEntry> entries = new CopyOnWriteArrayList<>();
    private volatile boolean committed;

    public PaperNativeCommandRegistrar(Plugin plugin, NativeCommandRegistrar<CommandSender> fallback,
                                       Supplier<PnLogger> pnLogger) {
        this.plugin = plugin;
        this.fallback = fallback;
        this.pnLogger = pnLogger;
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            committed = true;
            Commands registrar = event.registrar();
            for (PaperCommandEntry entry : new ArrayList<>(entries)) {
                if (entry.isActive()) {
                    registerNative(registrar, entry);
                }
            }
        });
    }

    @Override
    public PlatformCommandRegistration register(Object owner, CommandDefinition command,
                                                NativeCommandExecutor<CommandSender> execute,
                                                NativeCommandSuggester<CommandSender> suggest) {
        if (committed) {
            return fallback.register(owner, command, execute, suggest);
        }
        PaperCommandEntry entry = new PaperCommandEntry(owner instanceof Plugin ? (Plugin) owner : null, command,
            execute, suggest);
        entries.add(entry);
        return () -> {
            // Paper cannot remove a command from its tree; the command simply stops answering until the next rebuild.
            entry.deactivate();
            entries.remove(entry);
            PlatformCommandRegistration registration = entry.getFallbackRegistration();
            if (registration != null) {
                registration.close();
            }
        };
    }

    private void registerNative(Commands registrar, PaperCommandEntry entry) {
        Plugin owner = entry.getOwner() != null ? entry.getOwner() : plugin;
        try {
            NativeInvocation<CommandSourceStack> invocation = new PaperEntryInvocation(entry, pnLogger);
            registrar.register(owner.getPluginMeta(), BrigadierTrees.build(entry.getCommand(), invocation),
                "pnLibrary portable command", new ArrayList<>(entry.getCommand().getAliases()));
        } catch (Exception error) {
            pnLogger.get().warning("Command /" + entry.getCommand().getName()
                + " could not use Paper's command API; using the classic command map", error);
            entry.setFallbackRegistration(
                fallback.register(owner, entry.getCommand(), entry.getExecute(), entry.getSuggest()));
        }
    }
}
