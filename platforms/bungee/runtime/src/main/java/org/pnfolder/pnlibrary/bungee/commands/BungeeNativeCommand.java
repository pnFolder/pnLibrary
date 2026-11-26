package org.pnfolder.pnlibrary.bungee.commands;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.core.commands.engine.TabSuggestions;
import org.pnfolder.pnlibrary.spi.commands.NativeCommandExecutor;
import org.pnfolder.pnlibrary.spi.commands.NativeCommandSuggester;

/** A Bungee command that hands execution and tab completion to the shared command dispatcher. */
final class BungeeNativeCommand extends Command implements TabExecutor {
    private final CommandDefinition definition;
    private final NativeCommandExecutor<CommandSender> onExecute;
    private final NativeCommandSuggester<CommandSender> onSuggest;

    BungeeNativeCommand(
            CommandDefinition definition,
            NativeCommandExecutor<CommandSender> onExecute,
            NativeCommandSuggester<CommandSender> onSuggest) {
        super(definition.getName(), null, definition.getAliases().toArray(new String[0]));
        this.definition = definition;
        this.onExecute = onExecute;
        this.onSuggest = onSuggest;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        onExecute.execute(sender, definition.getName(), args);
    }

    // Bungee tab completion is synchronous; do not block the proxy beyond the suggestion budget.
    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        return TabSuggestions.suggestionsWithin(
                onSuggest.suggest(sender, definition.getName(), args), TabSuggestions.PROXY_SUGGESTION_MILLIS);
    }
}
