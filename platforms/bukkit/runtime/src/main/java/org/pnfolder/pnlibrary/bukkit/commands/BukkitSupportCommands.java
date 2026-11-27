package org.pnfolder.pnlibrary.bukkit.commands;

import org.bukkit.plugin.Plugin;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.api.commands.CommandSender;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;

/** The {@code /pn} support helpers: the support link and the diagnostic test errors. */
final class BukkitSupportCommands {
    private final BukkitCommandReplies replies;
    private final BukkitDiagnosticTestErrors testErrors;

    BukkitSupportCommands(Plugin plugin, PnLibrary library, BukkitCommandReplies replies) {
        this.replies = replies;
        this.testErrors = new BukkitDiagnosticTestErrors(plugin, library);
    }

    void support(CommandSender reply) {
        replies.say(reply, ChatTone.INFO, "Поддержка pnFolder: " + PnLibraryIdentity.SUPPORT_URL);
    }

    void error(CommandSender reply) {
        testErrors.unique(replies.reported(reply));
    }

    /** Logs the same test error {@code count} times, or the default number of times when {@code null}. */
    void errorRepeat(CommandSender reply, Integer count) {
        testErrors.repeated(replies.reported(reply), count);
    }

    void errorChain(CommandSender reply) {
        testErrors.chained(replies.reported(reply));
    }
}
