package org.pnfolder.pnlibrary.core.commands.proxy;

import java.util.Arrays;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.CommandContext;

/** Builds the context of a command typed on a proxy; replies to the proxy control command name it with {@link ProxyCommandNames}. */
public final class ProxyCommandContext {
    private ProxyCommandContext() {
    }

    @NotNull
    public static CommandContext of(@NotNull Audience audience, @NotNull String alias, @NotNull String[] arguments) {
        String last = arguments.length == 0 ? "" : arguments[arguments.length - 1];
        return new CommandContext(new ProxyCommandSender(audience, ProxyCommandNames.owns(alias)), Arrays.asList(arguments), alias, last);
    }
}
