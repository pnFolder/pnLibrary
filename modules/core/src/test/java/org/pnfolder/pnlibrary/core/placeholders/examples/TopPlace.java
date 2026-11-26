package org.pnfolder.pnlibrary.core.placeholders.examples;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

public class TopPlace {
    private final int place;
    private final String debug;

    public TopPlace(int place, String debug) {
        this.place = place;
        this.debug = debug;
    }

    @Placeholder
    public Reply<String> getName() {
        return Reply.of("player" + place);
    }

    @Placeholder
    public Reply<Integer> getKills() {
        return Reply.of(100 - place);
    }

    /** {@code {clan.war.top.3.debug2}} shows what the route looked like. */
    @Placeholder
    public Reply<String> getDebug2() {
        return Reply.of(debug);
    }
}
