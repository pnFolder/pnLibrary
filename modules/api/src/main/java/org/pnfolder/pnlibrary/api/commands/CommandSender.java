package org.pnfolder.pnlibrary.api.commands;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;

/**
 * Whoever ran a command: the console or a player. It is an Adventure {@link Audience}; its name, permissions and kind
 * are read with {@code name}, {@code hasPermission} and {@code isConsole} from
 * {@code org.pnfolder.pnlibrary.api.audience}.
 *
 * <pre>{@code
 * builder.executes(ctx -> ctx.getSender().sendMessage(Component.text("pong")));
 * }</pre>
 */
public interface CommandSender extends Audience {
    /**
     * Whether clickable and hoverable chat components reach this sender intact.
     *
     * <p>{@code false} for the console, and for players on a server whose chat fell back to plain text; show such
     * senders text that does not rely on buttons.
     */
    default boolean getSupportsInteraction() {
        return !AudienceExtensions.isConsole(this);
    }

    /** Sends one Adventure component to this sender. */
    @Deprecated
    default void send(@NotNull Component message) {
        sendMessage(message);
    }
}
