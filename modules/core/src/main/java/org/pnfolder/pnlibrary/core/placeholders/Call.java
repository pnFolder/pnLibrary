package org.pnfolder.pnlibrary.core.placeholders;

import java.util.Objects;
import org.pnfolder.pnlibrary.core.placeholders.address.ParsedArgument;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.placeholders.route.RouteInfo;

/** What a placeholder method sees: who asks and the text of every slot on the path. */
public final class Call {
    private final ViewerInfo viewer;
    private final Map<String, ParsedArgument> slots;
    private final RouteInfo route;

    Call(@NotNull ViewerInfo viewer, @NotNull Map<String, ParsedArgument> slots, @NotNull RouteInfo route) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.slots = Objects.requireNonNull(slots, "slots");
        this.route = Objects.requireNonNull(route, "route");
    }

    public ViewerInfo getViewer() {
        return viewer;
    }

    public RouteInfo getRoute() {
        return route;
    }

    public ParsedArgument slot(String name) {
        ParsedArgument found = slots.get(name);
        if (found == null) {
            throw new IllegalStateException("The path has no argument '" + name + "'");
        }
        return found;
    }
}
