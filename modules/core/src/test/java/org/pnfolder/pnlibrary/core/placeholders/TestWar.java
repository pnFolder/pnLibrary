package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Hide;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

public class TestWar {
    @Placeholder
    public final Reply<Integer> score = Reply.of(340);

    @Placeholder
    public final Reply<Integer> wins = Reply.of(2);

    @Hide
    @Placeholder
    public final Reply<String> secretKey = Reply.of("hidden");
}
