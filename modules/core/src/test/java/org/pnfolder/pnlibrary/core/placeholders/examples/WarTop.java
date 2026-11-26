package org.pnfolder.pnlibrary.core.placeholders.examples;

import java.util.ArrayList;
import java.util.List;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Route;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;
import org.pnfolder.pnlibrary.api.placeholders.route.RouteInfo;
import org.pnfolder.pnlibrary.api.placeholders.route.RouteKind;
import org.pnfolder.pnlibrary.api.placeholders.route.RoutePart;

public class WarTop {

    /** The branch {@code top} with an argument: {@code place} is the {@code 3}. */
    @Placeholder("Place in the war top")
    public Reply<TopPlace> top(@Route RouteInfo route, @Argument(range = "1..10") ArgumentValue<Integer> place) {
        // Everything the route knows, one line each.
        String group = route.getGroup(); // "clan"
        List<String> branches = texts(route.getBranches()); // ["war", "top"]
        String argument = route.getArguments().get(0).getText(); // "3", as the player wrote it
        String argumentName = route.getArguments().get(0).getName(); // "place", the parameter name
        String field = route.getField() == null ? null : route.getField().getText(); // "name"
        List<String> formats = names(route.getFormats()); // ["pad0", "upper"]
        String padWidth = padWidth(route); // "4"
        String path = route.getPath(); // "clan.war.top.3.name"
        List<String> belowWar = texts(route.after("war")); // ["top", "3", "name"]
        List<String> parts = new ArrayList<String>();
        for (RoutePart part : route.getParts()) {
            parts.add(part.getKind() + ":" + part.getText()); // GROUP:clan BRANCH:war BRANCH:top ARGUMENT:3 FIELD:name FORMAT:pad0 FORMAT:upper
        }
        boolean isField = route.of(RouteKind.FIELD).size() == 1; // true
        boolean loud = route.hasFormat("upper"); // true

        // Ending early with an answer that suits the asked field and format.
        if (place.required() > 5) {
            return Reply.stop("kills".equals(field) || route.hasFormat("pad0") ? (Object) 0 : "nobody");
        }
        String seen = group + "/" + branches.size() + "/" + argument + "/" + argumentName + "/" + formats + "/" + padWidth + "/" + path
                + "/" + belowWar + "/" + parts + "/" + isField + "/" + loud;
        return Reply.next(new TopPlace(place.required(), seen));
    }

    private static List<String> texts(List<RoutePart> parts) {
        List<String> texts = new ArrayList<String>();
        for (RoutePart part : parts) {
            texts.add(part.getText());
        }
        return texts;
    }

    private static List<String> names(List<RoutePart> parts) {
        List<String> names = new ArrayList<String>();
        for (RoutePart part : parts) {
            names.add(part.getName());
        }
        return names;
    }

    private static String padWidth(RouteInfo route) {
        for (RoutePart format : route.getFormats()) {
            if (format.getName().equals("pad0")) {
                return format.getArguments().isEmpty() ? null : format.getArguments().get(0);
            }
        }
        return null;
    }
}
