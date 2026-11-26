package org.pnfolder.pnlibrary.core.placeholders;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Viewer;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

@Placeholders("reply")
public class TestReplyValues {
    @Placeholder
    public Reply<Integer> level(@Viewer ViewerInfo viewer) {
        if (viewer.getPlayerId() == null) {
            return Reply.none();
        }
        return Reply.value(12);
    }

    @Placeholder
    public Reply<Long> money() {
        return Reply.value(1_250L, "coins");
    }

    @Placeholder
    public Reply<TestReplyWar> war(@Viewer ViewerInfo viewer) {
        if (viewer.getPlayerId() == null) {
            return Reply.stop("no clan");
        }
        return Reply.next(new TestReplyWar(340, 2));
    }

    @Placeholder
    public CompletableFuture<Reply<TestReplyWar>> warLater(@Viewer final ViewerInfo viewer) {
        return CompletableFuture.supplyAsync(() -> {
            if (viewer.getPlayerId() == null) {
                return Reply.<TestReplyWar>stop("no clan later");
            }
            return Reply.next(new TestReplyWar(5, 1));
        });
    }

    @Placeholder
    public Reply<TestReplyWar> plainWar() {
        return Reply.next(new TestReplyWar(1, 1));
    }

    @Placeholder
    public Reply<Duration> left() {
        return Reply.of(Duration.ofMinutes(95));
    }
}
