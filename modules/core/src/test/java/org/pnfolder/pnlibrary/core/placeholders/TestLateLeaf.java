package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

public class TestLateLeaf {
    @Placeholder
    public final Reply<Integer> score = Reply.of(7);

    @Placeholder
    public final Reply<String> label = Reply.of("late");
}
