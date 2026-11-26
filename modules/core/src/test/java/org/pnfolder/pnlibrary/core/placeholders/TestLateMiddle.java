package org.pnfolder.pnlibrary.core.placeholders;

import java.util.concurrent.CompletableFuture;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

public class TestLateMiddle {
    @Placeholder
    public CompletableFuture<Reply<TestLateLeaf>> deeper() {
        return CompletableFuture.supplyAsync(() -> Reply.next(new TestLateLeaf()));
    }
}
