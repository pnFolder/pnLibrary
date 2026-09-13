package ru.privatenull.pnlibrary.api.currency

import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class CurrencyKey(val owner: PluginId, val name: String) {
    init { require(name.matches(Regex("[a-z0-9_.-]+"))) { "Invalid currency name: $name" } }
    override fun toString(): String = "${owner.value}:$name"
}

data class CurrencyAccount(val playerId: UUID)

enum class CurrencyCapability {
    BALANCE, DEPOSIT, WITHDRAW, SET_BALANCE, RESET, TRANSFER, OFFLINE_ACCOUNTS,
    FRACTIONAL_AMOUNTS, FORMATTING, TRANSACTION_HISTORY,
}

data class CurrencyDefinition @JvmOverloads constructor(
    val displayName: String,
    val symbol: String = "",
    val fractionDigits: Int = 2,
    val roundingMode: RoundingMode = RoundingMode.DOWN,
) {
    init {
        require(displayName.isNotBlank()) { "currency displayName must not be blank" }
        require(fractionDigits in 0..18) { "currency fractionDigits must be between 0 and 18" }
    }

    fun normalize(amount: BigDecimal): BigDecimal = amount.setScale(fractionDigits, roundingMode)
    fun accepts(amount: BigDecimal): Boolean = amount.signum() >= 0 && normalize(amount).compareTo(amount) == 0
}

enum class CurrencyResultStatus { SUCCESS, REJECTED, UNSUPPORTED, UNAVAILABLE, FAILED }
enum class CurrencyRejectReason { INVALID_AMOUNT, INSUFFICIENT_FUNDS, ACCOUNT_NOT_FOUND, LIMIT_EXCEEDED, PROVIDER_REJECTED }

data class CurrencyResult(
    val status: CurrencyResultStatus,
    val previousBalance: BigDecimal? = null,
    val currentBalance: BigDecimal? = null,
    val reason: CurrencyRejectReason? = null,
    val message: String? = null,
    val error: Throwable? = null,
) {
    val isSuccess: Boolean get() = status == CurrencyResultStatus.SUCCESS

    companion object {
        @JvmStatic fun success(previous: BigDecimal? = null, current: BigDecimal? = null) =
            CurrencyResult(CurrencyResultStatus.SUCCESS, previous, current)
        @JvmStatic fun rejected(reason: CurrencyRejectReason, message: String? = null) =
            CurrencyResult(CurrencyResultStatus.REJECTED, reason = reason, message = message)
        @JvmStatic fun unsupported(message: String? = null) =
            CurrencyResult(CurrencyResultStatus.UNSUPPORTED, message = message)
        @JvmStatic fun unavailable(message: String? = null) =
            CurrencyResult(CurrencyResultStatus.UNAVAILABLE, message = message)
        @JvmStatic fun failed(error: Throwable) =
            CurrencyResult(CurrencyResultStatus.FAILED, message = error.message, error = error)
    }
}

class CurrencyAccess private constructor(
    private val ownerAllowed: Boolean,
    private val allPlugins: Boolean,
    private val allowed: Set<PluginId>,
    private val patterns: Set<String>,
    private val denied: Set<PluginId>,
) {
    fun allows(owner: PluginId, consumer: PluginId): Boolean {
        if (consumer in denied) return false
        if (ownerAllowed && owner == consumer) return true
        if (allPlugins || consumer in allowed) return true
        return patterns.any { pattern ->
            Regex("^" + pattern.split('*').joinToString(".*", transform = Regex::escape) + "$", RegexOption.IGNORE_CASE)
                .matches(consumer.value)
        }
    }

    companion object {
        @JvmStatic fun ownerOnly() = Builder().owner().build()
        @JvmStatic fun shared() = Builder().owner().allowAllLibraryPlugins().build()
        @JvmStatic fun builder() = Builder()
    }

    class Builder {
        private var owner = false
        private var all = false
        private val allowed = linkedSetOf<PluginId>()
        private val patterns = linkedSetOf<String>()
        private val denied = linkedSetOf<PluginId>()
        fun owner() = apply { owner = true }
        fun allowAllLibraryPlugins() = apply { all = true }
        fun allow(vararg ids: String) = apply { ids.map(PluginId::of).forEach(allowed::add) }
        fun allowMatching(vararg values: String) = apply { patterns += values }
        fun deny(vararg ids: String) = apply { ids.map(PluginId::of).forEach(denied::add) }
        fun build() = CurrencyAccess(owner, all, allowed.toSet(), patterns.toSet(), denied.toSet())
    }
}
