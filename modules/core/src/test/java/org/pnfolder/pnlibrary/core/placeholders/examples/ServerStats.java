package org.pnfolder.pnlibrary.core.placeholders.examples;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

public class ServerStats {
    @Placeholder
    public Reply<Integer> getCpu() {
        return Reply.of(17);
    }

    @Placeholder
    public Reply<Integer> getMemory() {
        return Reply.of(42);
    }
}
