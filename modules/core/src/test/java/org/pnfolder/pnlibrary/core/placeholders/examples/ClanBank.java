package org.pnfolder.pnlibrary.core.placeholders.examples;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.annotation.UseFormat;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Viewer;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

/** A default format needs no {@code |name} in the text. On a method it covers one value, on a class every value of the type. */
@Placeholders("bank")
@UseFormat(value = "money", type = Long.class)
public class ClanBank {
    @Placeholder
    public Reply<Long> balance(@Viewer ViewerInfo viewer) {
        Clan clan = Clans.of(viewer.getPlayerId());
        return Reply.of(clan == null ? null : clan.getBalance());
    }

    /** The text can still name another format ({@code {bank.bonus|rank}}) or turn the default off ({@code {bank.bonus|raw}}). */
    @Placeholder
    @UseFormat("rank")
    public Reply<Integer> bonus(@Viewer ViewerInfo viewer) {
        return Reply.of(3);
    }
}
