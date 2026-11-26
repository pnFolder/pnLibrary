package org.pnfolder.pnlibrary.core.placeholders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;

class PlaceholderLateBranchTest {
    private final PlaceholderEngine engine = new PlaceholderEngine(problem -> { });

    private Object read(String address) throws Exception {
        return engine.resolve(address).toCompletableFuture().get();
    }

    @Test
    void aBranchMayBeAnsweredLaterAlsoSeveralInARow() throws Exception {
        engine.scope("test/late").register(new TestLateValues());

        assertEquals(7, read("late.branch.deeper.score"));
        assertEquals("late", read("late.branch.deeper.label"));
    }

    @Test
    void aBranchThatIsEmptyLaterEndsTheAddressQuietly() throws Exception {
        engine.scope("test/late").register(new TestLateValues());

        assertNull(read("late.missing.score"));
    }

    @Test
    void anAnswerFromALateBranchEndsTheAddress() throws Exception {
        engine.scope("test/late").register(new TestLateValues());

        assertEquals("stopped here", read("late.stopped.score"));
        assertEquals("big", read("late.withargument.9.score"));
        assertEquals(7, read("late.withargument.2.score"));
    }

    @Test
    void lateBranchesAreListedLikeOthers() {
        engine.scope("test/late").register(new TestLateValues());

        boolean listed = false;
        for (PlaceholderInfo info : engine.list()) {
            listed |= info.getAddress().equals("late.branch.deeper.score");
        }
        assertTrue(listed);
    }
}
