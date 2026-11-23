package org.pnfolder.pnlibrary.core.updates.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.updates.product.ProductId
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.state.BlockedReason
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot
import org.pnfolder.pnlibrary.api.updates.state.UpdateState
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import java.util.UUID

class UpdateStatusMessagesTest {
    private fun plugin(state: UpdateState, product: String = "shop", message: String? = null) = UpdateSnapshot(
        product, "1.0.0", "1.1.0", UpdateChannel.STABLE, state, 17, 17, false, null, message,
    )

    private fun plan(state: UpdateState, blockers: List<BlockedReason> = emptyList(), message: String? = null) =
        UpdatePlanSnapshot(UUID.randomUUID(), 0, state, null, blockers, message)

    @Test
    fun `a plugin without changes is up to date`() {
        val notice = UpdateStatusMessages.status(plan(UpdateState.UP_TO_DATE) to plugin(UpdateState.UP_TO_DATE)).single()

        assertEquals(ChatTone.SUCCESS, notice.tone)
        assertTrue(notice.text.startsWith("Плагин shop"))
    }

    @Test
    fun `a frozen plugin is reported as paused`() {
        val notice = UpdateStatusMessages.status(plan(UpdateState.UP_TO_DATE) to plugin(UpdateState.FROZEN)).single()

        assertEquals(ChatTone.WARNING, notice.tone)
    }

    @Test
    fun `a blocked plan lists each reason after the headline`() {
        val reasons = listOf(BlockedReason.Frozen(ProductId.of("shop")))
        val notices = UpdateStatusMessages.status(plan(UpdateState.BLOCKED, reasons) to plugin(UpdateState.UPDATE_AVAILABLE))

        assertEquals(2, notices.size)
        assertEquals(ChatTone.FAILURE, notices[0].tone)
        assertTrue(notices[1].text.startsWith("Причина:"))
    }

    @Test
    fun `a failure message ends with exactly one period`() {
        val notice = UpdateStatusMessages.status(plan(UpdateState.FAILED, message = "нет сети.") to plugin(UpdateState.UP_TO_DATE)).single()

        assertEquals("нет сети.", notice.detail)
    }

    @Test
    fun `an older required api advises updating the library`() {
        val range = org.pnfolder.pnlibrary.api.version.ApiVersionRange(5, 6)
        val text = UpdateStatusMessages.blocker(BlockedReason.ApiMismatch(ProductId.of("shop"), range, 3))

        assertTrue(text.contains("Сначала обновите библиотеку"))
        assertTrue(SemanticVersion.parse("1.0.0").toString().isNotEmpty())
    }
}
