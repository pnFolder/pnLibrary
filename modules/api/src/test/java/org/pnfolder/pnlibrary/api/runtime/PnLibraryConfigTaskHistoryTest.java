package org.pnfolder.pnlibrary.api.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PnLibraryConfigTaskHistoryTest {
    @Test
    void aNegativeTaskHistoryCapacityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PnLibraryConfig.builder().taskHistoryCapacity(-1).build());
    }
}
