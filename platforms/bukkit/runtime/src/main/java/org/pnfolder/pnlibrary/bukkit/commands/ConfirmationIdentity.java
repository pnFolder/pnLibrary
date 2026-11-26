package org.pnfolder.pnlibrary.bukkit.commands;

import java.time.Duration;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Who a confirmation token belongs to, and how long it stays valid. */
final class ConfirmationIdentity {
    /** How long a confirmation token stays valid. */
    static final Duration CONFIRMATION_TIME = Duration.ofSeconds(30);

    /** The identity the console's tokens are bound to. */
    private static final UUID CONSOLE_ID = new UUID(0L, 0L);

    private ConfirmationIdentity() {
    }

    /** A player's own id, or one fixed id for the console, so that both get tokens only they can use. */
    static UUID identityOf(CommandSender sender) {
        return sender instanceof Player ? ((Player) sender).getUniqueId() : CONSOLE_ID;
    }
}
