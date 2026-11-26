package org.pnfolder.pnlibrary.core.placeholders.examples;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Viewer;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

/** For whoever asks: {@link ViewerInfo} is filled by the library and is not a part of the address. */
@Placeholders("me")
public class MeValues {

    /** {@code {me.language}} gives {@code ru} or {@code en}. */
    @Placeholder
    public Reply<String> language(@Viewer ViewerInfo viewer) {
        return Reply.of(viewer.getLanguage());
    }

    /** The player may be missing (console, background work), so the value is nullable: {@code {me.id}} stays as written. */
    @Placeholder
    public Reply<String> id(@Viewer ViewerInfo viewer) {
        return Reply.of(viewer.getPlayerId() == null ? null : viewer.getPlayerId().toString());
    }

    /** Asked through PlaceholderAPI ({@code %pn_me_papi%}) or not. */
    @Placeholder
    public Reply<Boolean> papi(@Viewer ViewerInfo viewer) {
        return Reply.of(viewer.isViaPapi());
    }
}
