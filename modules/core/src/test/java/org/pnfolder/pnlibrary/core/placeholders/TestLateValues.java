package org.pnfolder.pnlibrary.core.placeholders;

import java.util.concurrent.CompletableFuture;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

@Placeholders("late")
public class TestLateValues {
    @Placeholder
    public CompletableFuture<Reply<TestLateMiddle>> branch() {
        return CompletableFuture.supplyAsync(() -> Reply.next(new TestLateMiddle()));
    }

    @Placeholder
    public CompletableFuture<Reply<TestLateLeaf>> missing() {
        return CompletableFuture.supplyAsync(() -> Reply.<TestLateLeaf>none());
    }

    @Placeholder
    public CompletableFuture<Reply<TestLateLeaf>> stopped() {
        return CompletableFuture.supplyAsync(() -> Reply.<TestLateLeaf>stop("stopped here"));
    }

    @Placeholder
    public CompletableFuture<Reply<TestLateLeaf>> withArgument(@Argument final ArgumentValue<Integer> id) {
        return CompletableFuture.supplyAsync(() -> {
            if (id.required() > 5) {
                return Reply.<TestLateLeaf>stop("big");
            }
            return Reply.next(new TestLateLeaf());
        });
    }
}
