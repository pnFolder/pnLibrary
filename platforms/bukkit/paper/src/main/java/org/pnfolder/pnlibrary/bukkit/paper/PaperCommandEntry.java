package org.pnfolder.pnlibrary.bukkit.paper;

import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.spi.commands.NativeCommandExecutor;
import org.pnfolder.pnlibrary.spi.commands.NativeCommandSuggester;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandRegistration;

/** One command queued for Paper's registration event. */
final class PaperCommandEntry {
    private final Plugin owner;
    private final CommandDefinition command;
    private final NativeCommandExecutor<CommandSender> execute;
    private final NativeCommandSuggester<CommandSender> suggest;
    private final AtomicBoolean suggestionFailureReported = new AtomicBoolean(false);
    private volatile boolean active = true;
    private volatile PlatformCommandRegistration fallbackRegistration;

    PaperCommandEntry(Plugin owner, CommandDefinition command, NativeCommandExecutor<CommandSender> execute,
                      NativeCommandSuggester<CommandSender> suggest) {
        this.owner = owner;
        this.command = command;
        this.execute = execute;
        this.suggest = suggest;
    }

    Plugin getOwner() {
        return owner;
    }

    CommandDefinition getCommand() {
        return command;
    }

    NativeCommandExecutor<CommandSender> getExecute() {
        return execute;
    }

    NativeCommandSuggester<CommandSender> getSuggest() {
        return suggest;
    }

    boolean isActive() {
        return active;
    }

    void deactivate() {
        active = false;
    }

    PlatformCommandRegistration getFallbackRegistration() {
        return fallbackRegistration;
    }

    void setFallbackRegistration(PlatformCommandRegistration registration) {
        fallbackRegistration = registration;
    }

    boolean markSuggestionFailureReported() {
        return suggestionFailureReported.compareAndSet(false, true);
    }
}
