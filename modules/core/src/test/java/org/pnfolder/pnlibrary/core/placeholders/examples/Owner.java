package org.pnfolder.pnlibrary.core.placeholders.examples;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

public class Owner {
    private final String ownerName;
    private final int ownerSince;

    public Owner(String ownerName, int ownerSince) {
        this.ownerName = ownerName;
        this.ownerSince = ownerSince;
    }

    @Placeholder("Name of the owner")
    public Reply<String> getName() {
        return Reply.of(ownerName);
    }

    @Placeholder("Since which year")
    public Reply<Integer> getSince() {
        return Reply.of(ownerSince);
    }
}
