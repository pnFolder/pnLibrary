package org.pnfolder.pnlibrary.api.currency

import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAccess
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.math.BigDecimal
import java.util.function.Consumer

/**
 * Globally unique currency identity composed of its owning plugin and local name.
 *
 * @property owner plugin that controls the registration lifecycle
 * @property name normalized lowercase local name using letters, digits, `_`, `.`, or `-`
 */
data class CurrencyKey(val owner: PluginId, val name: String) {
    init { require(name.matches(Regex("[a-z0-9_.-]+"))) { "Invalid currency name: $name" } }
    /** Returns the canonical `owner:name` reference accepted by [CurrencyService.get]. */
    override fun toString(): String = "${owner.value}:$name"
}

/** Lifecycle state of a currency registration. */
enum class CurrencyRegistrationState {
    /** Visible and available for operations. */ ACTIVE,
    /** Temporarily unavailable but eligible for [CurrencyRegistration.enable]. */ DISABLED,
    /** Permanently removed and no longer eligible for re-enabling. */ CLOSED,
}

/** Live currency registration that owns its provider and attached integrations. */
interface CurrencyRegistration : Currency, AutoCloseable {
    /** Current registration lifecycle state. */
    val state: CurrencyRegistrationState
    /** Makes a disabled registration visible and operational again. */
    fun enable()
    /** Temporarily hides the currency and makes mutations return unavailable results. */
    fun disable()
}

/** Options shared by lightweight, managed, and direct provider registrations. */
interface CurrencyRegistrationOptions {
    /** Replaces the complete consumer access policy. */
    fun access(access: CurrencyAccess): CurrencyRegistrationOptions
    /** Builds and replaces the access policy through a Java-friendly callback. */
    fun access(configure: Consumer<CurrencyAccess.Builder>): CurrencyRegistrationOptions
    /** Adds local aliases owned by the same plugin; conflicts fail during registration. */
    fun aliases(vararg aliases: String): CurrencyRegistrationOptions
}

/** Synchronous operation that atomically transfers one amount between accounts. */
fun interface CurrencyTransferOperation {
    /** Applies a transfer and returns its structured result. */
    fun apply(from: CurrencyAccount, to: CurrencyAccount, amount: BigDecimal): CurrencyResult
}

/** Definition builder for a lightweight currency backed by caller-supplied operations. */
interface CurrencyDefinitionBuilder : CurrencyRegistrationOptions {
    /** Configures display and decimal rules. */
    fun descriptor(configure: Consumer<CurrencyDescriptor.Builder>): CurrencyDefinitionBuilder
    /** Configures mandatory balance lookup and optional mutation capabilities. */
    fun operations(configure: Consumer<CurrencyOperations>): CurrencyDefinitionBuilder
}

/** Generated placeholder publication options for a managed currency. */
interface CurrencyPlaceholderOptions {
    /** Enables or disables generation of library placeholders. */
    fun enabled(value: Boolean): CurrencyPlaceholderOptions
    /** Sets which plugins may consume generated placeholders. */
    fun access(access: PlaceholderAccess): CurrencyPlaceholderOptions
    /** Enables or disables publication through the PlaceholderAPI adapter. */
    fun placeholderApi(enabled: Boolean): CurrencyPlaceholderOptions
    /** Enables PlaceholderAPI publication with explicit [namespace]. */
    fun placeholderApi(namespace: String): CurrencyPlaceholderOptions
}

/** Determines whether destructive administrative commands require a second approval step. */
enum class CurrencyConfirmationMode {
    /** Executes destructive operations immediately after permission checks. */ NONE,
    /** Requires a time-limited approval through the server console. */ CONSOLE,
}
