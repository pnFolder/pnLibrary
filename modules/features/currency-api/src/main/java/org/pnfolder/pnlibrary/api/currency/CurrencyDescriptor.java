package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable presentation and decimal-normalization rules for a currency.
 *
 * <p>For example, a descriptor with two fraction digits and {@link RoundingMode#DOWN} converts {@code 10.129} to
 * {@code 10.12}; {@link #accepts} rejects that input because normalization changes it.
 */
public final class CurrencyDescriptor {
    private final String displayName;
    private final String symbol;
    private final int fractionDigits;
    private final RoundingMode roundingMode;

    /**
     * Creates a descriptor.
     *
     * @param displayName human-readable singular or brand name
     * @param symbol short display symbol, which may be empty
     * @param fractionDigits required decimal scale from 0 through 18
     * @param roundingMode rule used when an amount has too many fraction digits
     */
    public CurrencyDescriptor(@NotNull String displayName, @NotNull String symbol, int fractionDigits,
                              @NotNull RoundingMode roundingMode) {
        if (CurrencyText.isBlank(displayName)) {
            throw new IllegalArgumentException("Currency display name must not be blank");
        }
        if (fractionDigits < 0 || fractionDigits > 18) {
            throw new IllegalArgumentException("Currency fraction digits must be between 0 and 18");
        }
        this.displayName = displayName;
        this.symbol = symbol;
        this.fractionDigits = fractionDigits;
        this.roundingMode = roundingMode;
    }

    /** Human-readable singular or brand name. */
    @NotNull
    public String getDisplayName() {
        return displayName;
    }

    /** Short display symbol, which may be empty. */
    @NotNull
    public String getSymbol() {
        return symbol;
    }

    /** Required decimal scale from 0 through 18. */
    public int getFractionDigits() {
        return fractionDigits;
    }

    /** Rule used when an amount has too many fraction digits. */
    @NotNull
    public RoundingMode getRoundingMode() {
        return roundingMode;
    }

    /** Returns {@code amount} at the configured scale using {@link #getRoundingMode()}. */
    @NotNull
    public BigDecimal normalize(@NotNull BigDecimal amount) {
        return amount.setScale(fractionDigits, roundingMode);
    }

    /** Returns whether {@code amount} already conforms without numerical rounding. */
    public boolean accepts(@NotNull BigDecimal amount) {
        try {
            return normalize(amount).compareTo(amount) == 0;
        } catch (ArithmeticException error) {
            return false;
        }
    }

    /** Creates the canonical fluent builder with the display {@code name}. */
    @NotNull
    public static Builder builder(@NotNull String name) {
        return new Builder(name);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CurrencyDescriptor)) {
            return false;
        }
        CurrencyDescriptor that = (CurrencyDescriptor) other;
        return fractionDigits == that.fractionDigits && displayName.equals(that.displayName)
                && symbol.equals(that.symbol) && roundingMode == that.roundingMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayName, symbol, fractionDigits, roundingMode);
    }

    @Override
    public String toString() {
        return "CurrencyDescriptor(displayName=" + displayName + ", symbol=" + symbol + ", fractionDigits="
                + fractionDigits + ", roundingMode=" + roundingMode + ")";
    }

    /** Mutable builder initialized with the display name. */
    public static final class Builder {
        private String displayName;
        private String symbol = "";
        private int fractionDigits = 2;
        private RoundingMode roundingMode = RoundingMode.DOWN;

        Builder(String name) {
            this.displayName = name;
        }

        /** Human-readable currency name. */
        @NotNull
        public String getDisplayName() {
            return displayName;
        }

        /** Sets the human-readable currency name. */
        public void setDisplayName(@NotNull String displayName) {
            this.displayName = displayName;
        }

        /** Short symbol appended or prepended by presentation code. */
        @NotNull
        public String getSymbol() {
            return symbol;
        }

        /** Sets the short symbol appended or prepended by presentation code. */
        public void setSymbol(@NotNull String symbol) {
            this.symbol = symbol;
        }

        /** Required amount scale. */
        public int getFractionDigits() {
            return fractionDigits;
        }

        /** Sets the required amount scale. */
        public void setFractionDigits(int fractionDigits) {
            this.fractionDigits = fractionDigits;
        }

        /** Normalization behavior for excess decimal places. */
        @NotNull
        public RoundingMode getRoundingMode() {
            return roundingMode;
        }

        /** Sets the normalization behavior for excess decimal places. */
        public void setRoundingMode(@NotNull RoundingMode roundingMode) {
            this.roundingMode = roundingMode;
        }

        /** Sets the human-readable name. */
        @NotNull
        public Builder displayName(@NotNull String value) {
            displayName = value;
            return this;
        }

        /** Sets the short presentation symbol. */
        @NotNull
        public Builder symbol(@NotNull String value) {
            symbol = value;
            return this;
        }

        /** Sets the required decimal scale. */
        @NotNull
        public Builder fractionDigits(int value) {
            fractionDigits = value;
            return this;
        }

        /** Configures an integer-only currency. */
        @NotNull
        public Builder wholeNumbers() {
            fractionDigits = 0;
            return this;
        }

        /** Sets the amount normalization mode. */
        @NotNull
        public Builder roundingMode(@NotNull RoundingMode value) {
            roundingMode = value;
            return this;
        }

        /** Validates and creates an immutable descriptor. */
        @NotNull
        public CurrencyDescriptor build() {
            return new CurrencyDescriptor(displayName, symbol, fractionDigits, roundingMode);
        }
    }
}
