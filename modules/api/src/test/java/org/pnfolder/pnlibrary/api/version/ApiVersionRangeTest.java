package org.pnfolder.pnlibrary.api.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiVersionRangeTest {
    @Test
    @DisplayName("supports inclusive API generation bounds")
    void supportsInclusiveApiGenerationBounds() {
        ApiVersionRange range = new ApiVersionRange(4, 5);

        assertFalse(range.supports(3));
        assertTrue(range.supports(4));
        assertTrue(range.supports(5));
        assertFalse(range.supports(6));
    }

    @Test
    @DisplayName("rejects non-positive and reversed API ranges")
    void rejectsNonPositiveAndReversedApiRanges() {
        assertThrows(IllegalArgumentException.class, () -> new ApiVersionRange(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ApiVersionRange(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ApiVersionRange(5, 4));
    }

    @Test
    @DisplayName("publishes the current independent API generation")
    void publishesTheCurrentIndependentApiGeneration() {
        assertEquals(3, PnLibraryApi.VERSION);
    }
}
