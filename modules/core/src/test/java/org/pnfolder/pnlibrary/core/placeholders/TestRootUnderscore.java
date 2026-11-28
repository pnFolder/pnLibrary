package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;
import org.pnfolder.pnlibrary.api.registry.Visibility;

@Placeholders(root = true)
public class TestRootUnderscore {
    @Placeholder
    public Reply<Integer> battle_wins() {
        return Reply.value(1);
    }

    @Placeholder(visibility = Visibility.PLUGIN)
    public Reply<Integer> hidden_one() {
        return Reply.value(2);
    }
}
