package org.pnfolder.pnlibrary.core.placeholders;

import java.util.List;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Invalid;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Route;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Viewer;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;
import org.pnfolder.pnlibrary.api.placeholders.route.RouteInfo;

@Placeholders("route")
public class TestRouteValues {
    private final List<RouteInfo> seen;

    public TestRouteValues(List<RouteInfo> seen) {
        this.seen = seen;
    }

    @Placeholder
    public Reply<TestRouteBranch> war(@Viewer ViewerInfo viewer, @Route RouteInfo route) {
        seen.add(route);
        if (viewer.getPlayerId() == null) {
            boolean score = route.getField() != null && route.getField().getText().equals("score");
            return Reply.stop(score ? (Object) 0 : "no clan");
        }
        return Reply.next(new TestRouteBranch(seen));
    }

    @Placeholder(onInvalid = Invalid.EMPTY)
    public Reply<TestRouteBranch> lost() {
        return Reply.none();
    }
}
