package org.pnfolder.pnlibrary.core.updates.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.chat.notice.ChatNotice;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.state.BlockedReason;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;

class UpdateStatusMessagesTest {
    private static UpdateSnapshot plugin(UpdateState state) {
        return new UpdateSnapshot("shop", "1.0.0", "1.1.0", UpdateChannel.STABLE, state, 17, 17, false, null, null);
    }

    private static UpdatePlanSnapshot plan(UpdateState state, List<BlockedReason> blockers, String message) {
        return new UpdatePlanSnapshot(UUID.randomUUID(), 0, state, null, blockers, message);
    }

    private static UpdatePlanSnapshot plan(UpdateState state) {
        return plan(state, Collections.<BlockedReason>emptyList(), null);
    }

    @Test
    void aPluginWithoutChangesIsUpToDate() {
        List<ChatNotice> notices = UpdateStatusMessages.status(plan(UpdateState.UP_TO_DATE), plugin(UpdateState.UP_TO_DATE));

        assertEquals(1, notices.size());
        assertEquals(ChatTone.SUCCESS, notices.get(0).getTone());
        assertTrue(notices.get(0).getText().startsWith("Плагин shop"));
    }

    @Test
    void aFrozenPluginIsReportedAsPaused() {
        List<ChatNotice> notices = UpdateStatusMessages.status(plan(UpdateState.UP_TO_DATE), plugin(UpdateState.FROZEN));

        assertEquals(ChatTone.WARNING, notices.get(0).getTone());
    }

    @Test
    void aBlockedPlanListsEachReasonAfterTheHeadline() {
        List<BlockedReason> reasons = Collections.<BlockedReason>singletonList(new BlockedReason.Frozen(ProductId.of("shop")));
        List<ChatNotice> notices = UpdateStatusMessages.status(plan(UpdateState.BLOCKED, reasons, null), plugin(UpdateState.UPDATE_AVAILABLE));

        assertEquals(2, notices.size());
        assertEquals(ChatTone.FAILURE, notices.get(0).getTone());
        assertTrue(notices.get(1).getText().startsWith("Причина:"));
    }

    @Test
    void aFailureMessageEndsWithExactlyOnePeriod() {
        List<ChatNotice> notices = UpdateStatusMessages.status(
            plan(UpdateState.FAILED, Collections.<BlockedReason>emptyList(), "нет сети."), plugin(UpdateState.UP_TO_DATE));

        assertEquals("нет сети.", notices.get(0).getDetail());
    }

    @Test
    void anOlderRequiredApiAdvisesUpdatingTheLibrary() {
        String text = UpdateStatusMessages.blocker(new BlockedReason.ApiMismatch(ProductId.of("shop"), new ApiVersionRange(5, 6), 3));

        assertTrue(text.contains("Сначала обновите библиотеку"));
    }
}
