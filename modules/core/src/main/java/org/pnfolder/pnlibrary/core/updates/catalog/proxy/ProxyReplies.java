package org.pnfolder.pnlibrary.core.updates.catalog.proxy;

import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogView;
import org.pnfolder.pnlibrary.core.updates.catalog.commands.UpdateCatalogRoutes;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.chat.ChatNotice;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.api.commands.CommandSender;
import org.pnfolder.pnlibrary.core.exceptions.Throwables;

/** The replies of the proxy update commands, shown as clickable chat or plain text depending on the sender. */
final class ProxyReplies {
    private ProxyReplies() {
    }

    static UpdateCatalogRoutes routes(CommandSender sender) {
        return ProxyUpdateCommand.ROUTES.forReader(sender.getSupportsInteraction(), AudienceExtensions.isConsole(sender), true);
    }

    static void say(CommandSender sender, ChatTone tone, String text) {
        say(sender, tone, text, null);
    }

    static void say(CommandSender sender, ChatTone tone, String text, @Nullable String detail) {
        notices(sender, Collections.singletonList(new ChatNotice(tone, text, detail)));
    }

    static void notices(CommandSender sender, List<ChatNotice> notices) {
        for (Component line : UpdateCatalogView.notices(notices, routes(sender))) {
            sender.sendMessage(line);
        }
    }

    static void notRegistered(CommandSender sender, String product) {
        say(sender, ChatTone.FAILURE, "Плагин " + product + " не зарегистрирован в pnLibrary.");
    }

    static void fail(CommandSender sender, String title, Throwable error) {
        say(sender, ChatTone.FAILURE, title + ".", Throwables.rootMessage(error));
    }
}
