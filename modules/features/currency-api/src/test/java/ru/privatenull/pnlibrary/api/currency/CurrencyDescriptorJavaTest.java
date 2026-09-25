package ru.privatenull.pnlibrary.api.currency;

import org.junit.jupiter.api.Test;

import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurrencyDescriptorJavaTest {
    @Test
    void canonicalBuilderIsDirectlyAvailableFromJava() {
        CurrencyDescriptor descriptor = CurrencyDescriptor.builder("Coins")
            .symbol("⛃")
            .fractionDigits(2)
            .roundingMode(RoundingMode.DOWN)
            .build();

        assertEquals("Coins", descriptor.getDisplayName());
        assertEquals("⛃", descriptor.getSymbol());
    }
}
