package org.pnfolder.pnlibrary.core.updates.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.updates.state.UpdateState

class CatalogLabelsTest {
    @Test
    fun `every state has a label, a short label and a color`() {
        UpdateState.entries.forEach { state ->
            assertTrue(CatalogLabels.stateLabel(state).isNotBlank(), state.name)
            assertTrue(CatalogLabels.shortStateLabel(state).length <= CatalogLabels.stateLabel(state).length, state.name)
            CatalogLabels.stateColor(state)
        }
    }

    @Test
    fun `short labels shorten only the long wordings`() {
        assertEquals("ждёт перезапуска", CatalogLabels.shortStateLabel(UpdateState.UPDATE_STAGED))
        assertEquals("актуально", CatalogLabels.shortStateLabel(UpdateState.UP_TO_DATE))
    }

    @Test
    fun `pending and problem sets do not overlap`() {
        assertTrue(CatalogLabels.PENDING.intersect(CatalogLabels.PROBLEMS).isEmpty())
    }

    @Test
    fun `an unknown operation reads as a state change`() {
        assertEquals("откат", CatalogLabels.operationLabel("ROLLBACK"))
        assertEquals("состояние", CatalogLabels.operationLabel("SOMETHING"))
    }
}
