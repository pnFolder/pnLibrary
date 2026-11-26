package org.pnfolder.pnlibrary.core.placeholders;

import java.util.List;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;
import org.pnfolder.pnlibrary.api.placeholders.route.RouteInfo;

public class TestRouteBranch {
    public final List<RouteInfo> seen;

    @Placeholder
    public final Reply<Integer> score = Reply.of(5);

    @Placeholder
    public final Reply<String> label = Reply.of("ok");

    public TestRouteBranch(List<RouteInfo> seen) {
        this.seen = seen;
    }
}
