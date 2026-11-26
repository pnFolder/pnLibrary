package org.pnfolder.pnlibrary.core.placeholders.examples;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Hide;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Viewer;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

// Example 1: the basics.
//
// The address is {group.branch.argument.field|format|format}.
// A class marked with @Placeholders is the group. Every @Placeholder member is a branch of it.

/** {clan.level} gives 12 for a player of the clan; {clan.war.score} gives 340; {clan.tag} gives WLF. */
@Placeholders("clan")
public class ClanBasics {

    /** A value. The description in the brackets is what {@code /pn placeholders} shows. */
    @Placeholder("Level of the clan")
    public Reply<Integer> level(@Viewer ViewerInfo viewer) {
        Clan clan = Clans.of(viewer.getPlayerId());
        return Reply.of(clan == null ? null : clan.getLevel());
    }

    /** A method that returns an object is a branch: the @Placeholder members of {@link WarBranch} continue the address. */
    @Placeholder
    public Reply<WarBranch> war(@Viewer ViewerInfo viewer) {
        Clan clan = Clans.of(viewer.getPlayerId());
        return Reply.next(clan == null ? null : new WarBranch(clan.getWar()));
    }

    /** A field works the same way: {@code {clan.tag}}. */
    @Placeholder
    public final Reply<String> tag = Reply.of("WLF");

    /** Marked with @Hide, so it is not a placeholder even though it has @Placeholder. */
    @Hide
    @Placeholder
    public Reply<String> secret() {
        return Reply.of("never shown");
    }
}
