package org.pnfolder.pnlibrary.bukkit.paper;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNode;

/** Decides whether a sender may see a command node in the client tree. */
final class PaperSenderAccess {
    private PaperSenderAccess() {
    }

    static boolean allowed(CommandSender sender, CommandNode node) {
        boolean console = sender instanceof ConsoleCommandSender;
        return hasPermission(sender, node, console) && senderFits(node, console);
    }

    private static boolean hasPermission(CommandSender sender, CommandNode node, boolean console) {
        String permission = node.getPermission();
        if (permission == null || (console && node.getConsoleBypassesPermission()) || sender.hasPermission(permission)) {
            return true;
        }
        for (String other : node.getAlsoPermissions()) {
            if (sender.hasPermission(other)) {
                return true;
            }
        }
        return false;
    }

    private static boolean senderFits(CommandNode node, boolean console) {
        switch (node.getSenderRule()) {
            case PLAYER:
                return !console;
            case CONSOLE:
                return console;
            default:
                return true;
        }
    }
}
