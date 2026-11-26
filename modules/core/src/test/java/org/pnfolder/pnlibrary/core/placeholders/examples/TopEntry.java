package org.pnfolder.pnlibrary.core.placeholders.examples;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

public class TopEntry {
    private final int place;

    public TopEntry(int place) {
        this.place = place;
    }

    @Placeholder
    public Reply<String> getName() {
        return Reply.of("player" + place);
    }

    @Placeholder
    public Reply<Integer> getKills() {
        return Reply.of(100 - place);
    }
}
