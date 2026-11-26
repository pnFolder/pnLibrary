package org.pnfolder.pnlibrary.core.placeholders.examples;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Viewer;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

/** Who asks. Language and the way the text was asked for are known; the player may be missing. */
@Placeholders("who")
public class AboutTheViewer {
    @Placeholder
    public Reply<String> hello(@Viewer ViewerInfo viewer) {
        return Reply.of(viewer.getLanguage().equals("ru") ? "Привет" : "Hello");
    }

    /** {@code viewer.requirePlayerId()} ends the placeholder quietly when there is no player (console, background work). */
    @Placeholder
    public Reply<String> id(@Viewer ViewerInfo viewer) {
        return Reply.of(viewer.requirePlayerId().toString());
    }
}
