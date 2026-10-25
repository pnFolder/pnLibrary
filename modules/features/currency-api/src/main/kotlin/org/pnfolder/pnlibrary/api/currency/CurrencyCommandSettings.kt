package org.pnfolder.pnlibrary.api.currency

import java.time.Duration

/**
 * Immutable command presentation and destructive-operation confirmation settings.
 *
 * Message strings may contain legacy `&` or `§` color codes and documented brace tokens
 * such as `{currency}`, `{amount}`, `{balance}`, and `{error}`. The defaults use the legacy
 * colors of pnLibrary's chat palette. Exact command availability and rendering remain
 * platform-specific.
 *
 * @property enabled whether platform command integration is exposed
 * @property permissionPrefix custom permission root, or `null` for the generated default
 * @property prefix component text prepended to command responses
 * @property success template for successful mutations
 * @property failure template for failed or rejected operations
 * @property balance template for balance lookup
 * @property historyEmpty response used when a ledger query returns no rows
 * @property confirmationMode second-step policy for destructive administration
 * @property confirmationTimeout confirmation lifetime from 10 seconds through 10 minutes
 */
data class CurrencyCommandSettings(
    val enabled: Boolean = true,
    val permissionPrefix: String? = null,
    val prefix: String = "&7[&d{currency}&7] ",
    val success: String = "&aОперация выполнена: &f{amount}",
    val failure: String = "&c{error}",
    val balance: String = "&bБаланс: &f{balance}",
    val historyEmpty: String = "&bОпераций пока нет.",
    val confirmationMode: CurrencyConfirmationMode = CurrencyConfirmationMode.CONSOLE,
    val confirmationTimeout: Duration = Duration.ofSeconds(60),
) {
    init {
        require(confirmationTimeout in MIN_CONFIRMATION_TIMEOUT..MAX_CONFIRMATION_TIMEOUT) {
            "Currency confirmation timeout must be between 10 seconds and 10 minutes"
        }
    }

    private companion object {
        val MIN_CONFIRMATION_TIMEOUT: Duration = Duration.ofSeconds(10)
        val MAX_CONFIRMATION_TIMEOUT: Duration = Duration.ofMinutes(10)
    }
}
