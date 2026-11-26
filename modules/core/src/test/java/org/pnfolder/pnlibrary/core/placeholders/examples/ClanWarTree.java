package org.pnfolder.pnlibrary.core.placeholders.examples;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Viewer;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

// Example 7: one address taken apart.
//
//   {clan.war.top.3.name|pad0:4|upper}
//
//    clan   .war     .top     .3        .name   |pad0:4        |upper
//    GROUP   BRANCH   BRANCH   ARGUMENT  FIELD   FORMAT         FORMAT
//                                                 arguments=[4]  arguments=[]
//
// The method `top` of WarTop receives this route and reads every part of it.
//
// Registration of the whole example:
//   context.getPlaceholders().formats(new WarFormats());
//   context.getPlaceholders().register(new ClanWarTree());
// Asking:
//   render("{clan.war.top.3.name|upper}", player)      // PLAYER3
//   render("{clan.war.top.3.kills|pad0:4}", player)    // 0097
//   render("{clan.war.top.99.name}", player)           // the place is outside 1..10: the address stays

@Placeholders("clan")
public class ClanWarTree {

    /** The branch {@code war}: the first method on the way. Its route is the whole address, not only the part up to {@code war}. */
    @Placeholder("War of the clan")
    public Reply<WarTop> war(@Viewer ViewerInfo viewer) {
        if (Clans.of(viewer.getPlayerId()) == null) {
            return Reply.stop("no clan");
        }
        return Reply.next(new WarTop());
    }
}
