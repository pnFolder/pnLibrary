package org.pnfolder.pnlibrary.core.placeholders.examples;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

public class DuelWar {
    private final WarInfo info;

    public DuelWar(WarInfo info) {
        this.info = info;
    }

    @Placeholder
    public Reply<Integer> getScore() {
        return Reply.of(info.getScore());
    }

    @Placeholder
    public Reply<Integer> getWins() {
        return Reply.of(info.getWins());
    }
}
