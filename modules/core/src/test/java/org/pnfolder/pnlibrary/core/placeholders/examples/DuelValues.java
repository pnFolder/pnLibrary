package org.pnfolder.pnlibrary.core.placeholders.examples;

import java.util.ArrayList;
import java.util.List;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Invalid;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Route;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Viewer;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;
import org.pnfolder.pnlibrary.api.placeholders.route.RouteInfo;
import org.pnfolder.pnlibrary.api.placeholders.route.RouteKind;
import org.pnfolder.pnlibrary.api.placeholders.route.RoutePart;

// Example 6: the way of the address, and ending it early.
//
// For {duel.war.enemy.leader.kills|pad0:4} the parts are:
//   duel = GROUP, war = BRANCH, enemy = BRANCH, leader = BRANCH, kills = FIELD, pad0 = FORMAT (arguments ["4"]).
// An argument in the path ({duel.top.3.name}, the 3) is an ARGUMENT part.

@Placeholders("duel")
public class DuelValues {

    /**
     * {@code Reply.stop(...)} ends the whole address with that value: nothing below {@code war} is called.
     * The route tells which field was asked, so the answer can differ: a number for {@code score}, a text for the rest.
     */
    @Placeholder("The war of the clan")
    public Reply<DuelWar> war(@Viewer ViewerInfo viewer, @Route RouteInfo route) {
        Clan clan = Clans.of(viewer.getPlayerId());
        if (clan == null) {
            boolean score = route.getField() != null && route.getField().getText().equals("score");
            return Reply.stop(score ? (Object) 0 : "no clan");
        }
        return Reply.next(new DuelWar(clan.getWar()));
    }

    /** {@code onInvalid} of a branch covers the text placeholders below it: {@code {duel.lost.name}} shows nothing instead of the address. */
    @Placeholder(onInvalid = Invalid.EMPTY)
    public Reply<DuelWar> lost() {
        return Reply.none();
    }

    /** Looking at the parts: formats and arguments are there too. */
    @Placeholder
    public Reply<String> explain(@Route RouteInfo route) {
        List<String> parts = new ArrayList<>();
        for (RoutePart part : route.getParts()) {
            parts.add(part.getKind().name().toLowerCase(java.util.Locale.ROOT) + "=" + part.getText());
        }
        List<String> formats = new ArrayList<>();
        for (RoutePart format : route.of(RouteKind.FORMAT)) {
            formats.add(format.getName());
        }
        return Reply.of(String.join(", ", parts) + (formats.isEmpty() ? "" : " (formats: " + String.join(", ", formats) + ")"));
    }
}
