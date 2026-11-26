package org.pnfolder.pnlibrary.core.placeholders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.placeholders.route.RouteInfo;
import org.pnfolder.pnlibrary.api.placeholders.route.RouteKind;
import org.pnfolder.pnlibrary.api.placeholders.route.RoutePart;

class PlaceholderRouteTest {
    private final PlaceholderEngine engine = new PlaceholderEngine(problem -> { });
    private final List<RouteInfo> seen = new ArrayList<>();

    private Object read(String address) throws Exception {
        return read(address, ViewerInfo.none());
    }

    private Object read(String address, ViewerInfo viewer) throws Exception {
        return engine.resolve(address, viewer).toCompletableFuture().get();
    }

    @Test
    void aBranchMethodSeesWhatEveryPartOfTheAddressIs() throws Exception {
        engine.scope("test/route").register(new TestRouteValues(seen));
        read("route.war.score|pad0:4", ViewerInfo.of(UUID.randomUUID()));

        assertEquals(1, seen.size());
        RouteInfo route = seen.get(0);
        List<RouteKind> kinds = new ArrayList<>();
        for (RoutePart part : route.getParts()) {
            kinds.add(part.getKind());
        }
        assertEquals(Arrays.asList(RouteKind.GROUP, RouteKind.BRANCH, RouteKind.FIELD, RouteKind.FORMAT), kinds);
        assertEquals("route.war.score", route.getPath());
        assertEquals(Collections.singletonList("4"), route.of(RouteKind.FORMAT).get(0).getArguments());
        assertTrue(route.hasFormat("pad0"));
        List<String> fields = new ArrayList<>();
        for (RoutePart part : route.after("war")) {
            if (part.getKind() == RouteKind.FIELD) {
                fields.add(part.getText());
            }
        }
        assertEquals(Collections.singletonList("score"), fields);
    }

    @Test
    void answerEndsTheAddressWithItsOwnValue() throws Exception {
        engine.scope("test/route").register(new TestRouteValues(seen));

        assertEquals(0, read("route.war.score"));
        assertEquals("no clan", read("route.war.label"));
    }

    @Test
    void aBranchWithoutValueCoversItsTextPlaceholders() throws Exception {
        engine.scope("test/route").register(new TestRouteValues(seen), new TestTailValues());

        assertEquals("", read("route.lost.score"));
        assertEquals("", read("route.lost.label"));
        assertEquals("-", read("tail.lost.label"));
    }
}
