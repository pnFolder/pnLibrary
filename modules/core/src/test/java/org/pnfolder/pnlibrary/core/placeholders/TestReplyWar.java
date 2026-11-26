package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

public class TestReplyWar {
    private final int points;
    private final int victories;

    public TestReplyWar(int points, int victories) {
        this.points = points;
        this.victories = victories;
    }

    @Placeholder
    public Reply<Integer> getScore() {
        return Reply.of(points);
    }

    @Placeholder
    public Reply<Integer> getWins() {
        return Reply.of(victories);
    }
}
