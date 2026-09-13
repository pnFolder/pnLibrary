package ru.privatenull.pnlibrary.api.currency

import java.math.BigDecimal
import java.math.RoundingMode

/** Immutable presentation and amount rules for a currency. */
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

    fun normalize(amount: BigDecimal): BigDecimal = amount.setScale(fractionDigits, roundingMode)
    fun accepts(amount: BigDecimal): Boolean = normalize(amount).compareTo(amount) == 0

    class Builder(name: String) {
        var displayName: String = name
        var symbol: String = ""
        var fractionDigits: Int = 2
        var roundingMode: RoundingMode = RoundingMode.DOWN

        fun displayName(value: String) = apply { displayName = value }
        fun symbol(value: String) = apply { symbol = value }
        fun fractionDigits(value: Int) = apply { fractionDigits = value }
        fun wholeNumbers() = apply { fractionDigits = 0 }
        fun roundingMode(value: RoundingMode) = apply { roundingMode = value }
        fun build() = CurrencyDescriptor(displayName, symbol, fractionDigits, roundingMode)
    }
}
