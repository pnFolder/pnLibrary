package org.pnfolder.pnlibrary.api.audienceB2;

import java.util.List;
import java.util.UUID;
import net.kyori.adventure.audience.ForwardingAudience;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Everyone the library can reach on this server: the console and every online player. It is an Adventure
 * {@link net.kyori.adventure.audience.Audience} itself, so a message sent to it goes to all of them, and
 * {@code filterAudience} narrows it. One receiver that fails does not stop the others.
 *
 * <pre>{@code
 * Audiences audiences = library.audiences();
 * audiences.sendMessage(Component.text("The console and every player read this"));
 * OnlinePlayer player = audiences.player(uuid);
 * audiences.filterAudience(it -> it.get(PermissionChecker.POINTER).map(c -> c.test("shop.admin")).orElse(false))
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
    @Nullable OnlinePlayer player(@NotNull String name);
}
