package org.pnfolder.pnlibrary.api.audience;

import java.util.List;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Everyone the library can reach on this server: the console and every online player. It is an Adventure
 * {@link Audience} itself, so a message sent to it goes to all of them, and Adventure's own {@code filterAudience}
 * narrows it. Each receiver is asked again on every operation, and one that fails does not stop the others.
 *
 * <pre>{@code
 * Audiences audiences = library.getAudiences();
 * audiences.sendMessage(Component.text("The console and every player read this"));
 * audiences.console().sendMessage(Component.text("Started"));
 * OnlinePlayer player = audiences.player(uuid);
 * audiences.filterAudience(it -> AudienceExtensions.hasPermission(it, "shop.admin"))
 *     .sendMessage(Component.text("Shop reloaded"));
 * }</pre>
 */
public interface Audiences extends ForwardingAudience {
    /** The server console. */
    @NotNull Console console();

    /** A snapshot of all players currently online. */
    @NotNull List<OnlinePlayer> players();

    /** The online player with {@code uniqueId}, or {@code null} when absent. */
    @Nullable OnlinePlayer player(@NotNull UUID uniqueId);

    /** The online player named {@code name}, ignoring case, or {@code null} when absent. */
    default @Nullable OnlinePlayer player(@NotNull String name) {
        for (OnlinePlayer player : players()) {
            if (AudienceExtensions.name(player).equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    /** Adapts a platform-native sender object (such as a Bukkit {@code CommandSender}), or {@code null} when unsupported. */
    @Nullable Audience of(@NotNull Object nativeSender);
}
