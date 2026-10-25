package org.pnfolder.pnlibrary.api.currency

import java.util.function.Consumer

/**
 * Definition builder for a storage-backed currency with an auditable ledger.
 *
 * Exactly one storage method must be called before registration. Use [storage]
 * when several currencies share a store whose lifecycle is managed elsewhere,
 * or [ownedStorage] when this registration should close it automatically.
 */
interface ManagedCurrencyBuilder : CurrencyRegistrationOptions {
    /** Configures display and decimal rules. */
    fun descriptor(configure: Consumer<CurrencyDescriptor.Builder>): ManagedCurrencyBuilder
    /** Uses shared storage without taking ownership of its lifecycle. */
    fun storage(storage: CurrencyStorage): ManagedCurrencyBuilder
    /** Uses storage owned and closed by this currency registration. */
    fun ownedStorage(storage: CurrencyStorage): ManagedCurrencyBuilder
    /** Sets the nonblank service identifier written to API-generated ledger entries. */
    fun service(name: String): ManagedCurrencyBuilder
    /** Configures generated balance, formatted-value, and symbol placeholders. */
    fun placeholders(configure: Consumer<CurrencyPlaceholderOptions>): ManagedCurrencyBuilder
    /** Configures platform command presentation and confirmation behavior. */
    fun commands(configure: Consumer<CurrencyCommandOptions>): ManagedCurrencyBuilder
}
