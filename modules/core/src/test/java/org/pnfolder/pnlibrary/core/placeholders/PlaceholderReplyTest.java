package org.pnfolder.pnlibrary.core.placeholders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;

class PlaceholderReplyTest {
    private final PlaceholderEngine engine = new PlaceholderEngine(problem -> { });
    private final ViewerInfo player = ViewerInfo.of(UUID.randomUUID());

    PlaceholderReplyTest() {
        PlaceholderService module = engine.scope("test/reply");
        module.formats(new TestReplyFormats());
        module.register(new TestReplyValues());
    }

    private Object read(String address) throws Exception {
        return read(address, ViewerInfo.none());
    }

    private Object read(String address, ViewerInfo viewer) throws Exception {
        return engine.resolve(address, viewer).toCompletableFuture().get();
    }

    @Test
    void valueAndNone() throws Exception {
        assertEquals(12, read("reply.level", player));
        assertNull(read("reply.level"));
    }

    @Test
    void aValueBringsItsOwnFormat() throws Exception {
        assertEquals("1250 coins", read("reply.money"));
        assertEquals(1250L, read("reply.money|raw"));
    }

    @Test
    void nextGoesOnAndStopAnswersFromTheBranch() throws Exception {
        assertEquals(340, read("reply.war.score", player));
        assertEquals("no clan", read("reply.war.score"));
        assertEquals("no clan", read("reply.war.wins"));
    }

    @Test
    void aReplyMayComeLater() throws Exception {
        assertEquals(5, read("reply.warlater.score", player));
        assertEquals("no clan later", read("reply.warlater.score"));
    }

    @Test
    void aPlainObjectStillContinuesTheAddress() throws Exception {
        assertEquals(1, read("reply.plainwar.score"));
    }

    @Test
    void aDurationReadsAsTimeWithoutAskingForAFormat() throws Exception {
        assertEquals("1h 35m", read("reply.left"));
    }

    @Test
    void theListShowsBranchesByWhatReplyHolds() {
        List<String> addresses = new ArrayList<String>();
        for (PlaceholderInfo info : engine.list()) {
            addresses.add(info.getAddress());
        }
        assertTrue(addresses.contains("reply.war.score"));
        assertTrue(addresses.contains("reply.level"));
    }
}
