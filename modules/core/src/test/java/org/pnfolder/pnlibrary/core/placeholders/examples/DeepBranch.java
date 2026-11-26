package org.pnfolder.pnlibrary.core.placeholders.examples;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

public class DeepBranch {
    private final String below;

    public DeepBranch(String below) {
        this.below = below;
    }

    /** {@code {route.deep.ending}}: {@code below} is {@code ending}, the parts after {@code deep}. */
    @Placeholder
    public Reply<String> getEnding() {
        return Reply.of(below);
    }
}
