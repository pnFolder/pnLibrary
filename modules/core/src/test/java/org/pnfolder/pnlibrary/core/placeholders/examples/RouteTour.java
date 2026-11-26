package org.pnfolder.pnlibrary.core.placeholders.examples;

import java.util.ArrayList;
import java.util.List;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Route;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;
import org.pnfolder.pnlibrary.api.placeholders.route.RouteInfo;
import org.pnfolder.pnlibrary.api.placeholders.route.RouteKind;
import org.pnfolder.pnlibrary.api.placeholders.route.RoutePart;

// Every way to read the route. Registered like any group: placeholders.register(new RouteTour()).
// Ask {route.top.3.name|pad0:4|upper} and each method below sees the same parts.

@Placeholders("route")
public class RouteTour {

    /** All parts in the order written, each with its kind: {@code {route.show.x}} gives the whole list. */
    @Placeholder
    public Reply<String> show(@Route RouteInfo route, @Argument ArgumentValue<String> text) {
        List<String> parts = new ArrayList<>();
        for (RoutePart part : route.getParts()) {
            parts.add(part.getKind() + ":" + part.getText());
        }
        return Reply.of(String.join(" ", parts)); // GROUP:route BRANCH:show ARGUMENT:x
    }

    /** Short readable text of the route, good for logs and error messages. */
    @Placeholder
    public Reply<String> log(@Route RouteInfo route) {
        return Reply.of(route.toString()); // group:route.field:log
    }

    /** The address without formats, and the group. {@code {route.path}} gives {@code route.path}, group {@code route}. */
    @Placeholder
    public Reply<String> path(@Route RouteInfo route) {
        return Reply.of(route.getPath() + " in " + route.getGroup());
    }

    /** The last word with the value: it tells which field of a branch is read. */
    @Placeholder
    public Reply<TopEntry> top(@Route RouteInfo route, @Argument ArgumentValue<Integer> place) {
        // `name` for {route.top.3.name}, `kills` for {route.top.3.kills}
        String field = route.getField() == null ? null : route.getField().getText();
        // the `3`: kind ARGUMENT, text "3", name = the parameter name
        RoutePart argument = route.of(RouteKind.ARGUMENT).get(0);
        if (place.required() > 10) {
            return Reply.stop(field + " of place " + argument.getText() + ": nobody"); // ends here, with a text
        }
        return Reply.next(new TopEntry(place.required()));
    }

    /** Formats written after the bar, with their arguments: {@code {route.formats|pad0:4|upper}}. */
    @Placeholder
    public Reply<String> formats(@Route RouteInfo route) {
        List<String> formats = new ArrayList<>();
        for (RoutePart format : route.of(RouteKind.FORMAT)) {
            formats.add(format.getName() + "(" + String.join(",", format.getArguments()) + ")");
        }
        String written = String.join(", ", formats);
        return Reply.value(route.hasFormat("raw") ? "raw: " + written : written); // pad0(4), upper()
    }

    /** What lies below a branch: the parts after the first part called {@code deep}. */
    @Placeholder
    public Reply<DeepBranch> deep(@Route RouteInfo route) {
        List<String> below = new ArrayList<>();
        for (RoutePart part : route.after("deep")) {
            below.add(part.getText());
        }
        return Reply.next(new DeepBranch(String.join(".", below)));
    }
}
