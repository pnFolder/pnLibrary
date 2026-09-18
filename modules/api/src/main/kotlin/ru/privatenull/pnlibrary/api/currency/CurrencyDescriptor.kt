package ru.privatenull.pnlibrary.api.currency

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Immutable presentation and decimal-normalization rules for a currency.
 *
 * For example, a descriptor with two fraction digits and [RoundingMode.DOWN] converts
 * `10.129` to `10.12`; [accepts] rejects that input because normalization changes it.
 *
 * @property displayName human-readable singular or brand name
 * @property symbol short display symbol, which may be empty
 * @property fractionDigits required decimal scale from 0 through 18
 * @property roundingMode rule used when an amount has too many fraction digits
 */
data class CurrencyDescriptor(
    val displayName: String,
    val symbol: String,
    val fractionDigits: Int,
    val roundingMode: RoundingMode,
) {
    init {
        require(displayName.isNotBlank()) { "Currency display name must not be blank" }
        require(fractionDigits in 0..18) { "Currency fraction digits must be between 0 and 18" }
    }

    /** Returns [amount] at the configured scale using [roundingMode]. */
    fun normalize(amount: BigDecimal): BigDecimal = amount.setScale(fractionDigits, roundingMode)

    /** Returns whether [amount] already conforms without numerical rounding. */
    fun accepts(amount: BigDecimal): Boolean = normalize(amount).compareTo(amount) == 0

    /** Mutable Java-friendly builder initialized with display [name]. */
    class Builder(name: String) {
        /** Human-readable currency name. */
        var displayName: String = name
        /** Short symbol appended or prepended by presentation code. */
        var symbol: String = ""
        /** Required amount scale. */
        var fractionDigits: Int = 2
        /** Normalization behavior for excess decimal places. */
        var roundingMode: RoundingMode = RoundingMode.DOWN

        /** Sets the human-readable name. */
        fun displayName(value: String) = apply { displayName = value }
        /** Sets the short presentation symbol. */
        fun symbol(value: String) = apply { symbol = value }
        /** Sets the required decimal scale. */
        fun fractionDigits(value: Int) = apply { fractionDigits = value }
        /** Configures an integer-only currency. */
        fun wholeNumbers() = apply { fractionDigits = 0 }
        /** Sets the amount normalization mode. */
        fun roundingMode(value: RoundingMode) = apply { roundingMode = value }
        /** Validates and creates an immutable descriptor. */
        fun build() = CurrencyDescriptor(displayName, symbol, fractionDigits, roundingMode)
    }
}
