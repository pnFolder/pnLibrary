package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

@Placeholders(root = true)
public class TestRootKeysWord {
    @Placeholder
    public Reply<Integer> keys() {
        return Reply.value(1);
    }
}
