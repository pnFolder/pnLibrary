package org.pnfolder.pnlibrary.core.commands.groups;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.core.commands.engine.CommandOutput;
import org.pnfolder.pnlibrary.core.commands.engine.SenderNotices;

/** The command prefix and the output helpers shared by the pages and the edits of {@code /pn groups}. */
final class GroupsChat {
    static final String ROOT = "/pn groups";

    private GroupsChat() {
    }

    @NotNull
    static ChatKit kit(@NotNull CommandContext context) {
        return CommandOutput.kit(context);
    }

    static void notice(@NotNull CommandContext context, @NotNull ChatTone tone, @NotNull String text, @Nullable String detail) {
        SenderNotices.notice(context.getSender(), tone, text, detail);
    }

    static void notice(@NotNull CommandContext context, @NotNull ChatTone tone, @NotNull String text) {
        notice(context, tone, text, null);
    }
}
