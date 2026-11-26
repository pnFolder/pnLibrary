package org.pnfolder.pnlibrary.api.architecture;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OptionalFeatureBoundaryTest {
    @Test
    @DisplayName("base API does not expose the optional currency contract")
    void baseApiDoesNotExposeTheOptionalCurrencyContract() {
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("org.pnfolder.pnlibrary.api.currency.CurrencyService"));
    }
}
