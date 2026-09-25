package ru.privatenull.pnlibrary.api.architecture

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class OptionalFeatureBoundaryTest {
    @Test
    fun `base API does not expose the optional currency contract`() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("ru.privatenull.pnlibrary.api.currency.CurrencyService")
        }
    }
}
