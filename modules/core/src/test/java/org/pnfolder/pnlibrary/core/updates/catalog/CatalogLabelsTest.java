package org.pnfolder.pnlibrary.core.updates.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;

class CatalogLabelsTest {
    @Test
    void everyStateHasALabelAShortLabelAndAColor() {
        for (UpdateState state : UpdateState.values()) {
            assertFalse(CatalogLabels.stateLabel(state).trim().isEmpty(), state.name());
            assertTrue(CatalogLabels.shortStateLabel(state).length() <= CatalogLabels.stateLabel(state).length(), state.name());
            CatalogLabels.stateColor(state);
        }
    }

    @Test
    void shortLabelsShortenOnlyTheLongWordings() {
        assertEquals("ждёт перезапуска", CatalogLabels.shortStateLabel(UpdateState.UPDATE_STAGED));
        assertEquals("актуально", CatalogLabels.shortStateLabel(UpdateState.UP_TO_DATE));
    }

    @Test
    void pendingAndProblemSetsDoNotOverlap() {
        Set<UpdateState> common = new HashSet<>(CatalogLabels.PENDING);
        common.retainAll(CatalogLabels.PROBLEMS);
        assertTrue(common.isEmpty());
    }

    @Test
    void anUnknownOperationReadsAsAStateChange() {
        assertEquals("откат", CatalogLabels.operationLabel("ROLLBACK"));
        assertEquals("состояние", CatalogLabels.operationLabel("SOMETHING"));
    }
}
