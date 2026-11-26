package org.pnfolder.pnlibrary.core.placeholders.examples;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

/** Reached through {@code war}, so the addresses are {@code {clan.war.score}} and {@code {clan.war.wins}}. No registration needed. */
public class WarBranch {
    private final WarInfo info;

    public WarBranch(WarInfo info) {
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
