package org.pnfolder.pnlibrary.currency

import org.pnfolder.pnlibrary.api.currency.CurrencyAccess
import org.pnfolder.pnlibrary.api.currency.CurrencyCommandOptions
import org.pnfolder.pnlibrary.api.currency.CurrencyCommandSettings
import org.pnfolder.pnlibrary.api.currency.CurrencyConfirmationMode
import org.pnfolder.pnlibrary.api.currency.CurrencyDefinitionBuilder
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor
import org.pnfolder.pnlibrary.api.currency.CurrencyKey
import org.pnfolder.pnlibrary.api.currency.CurrencyOperations
import org.pnfolder.pnlibrary.api.currency.CurrencyPlaceholderOptions
import org.pnfolder.pnlibrary.api.currency.CurrencyProvider
import org.pnfolder.pnlibrary.api.currency.CurrencyRegistration
import org.pnfolder.pnlibrary.api.currency.CurrencyRegistrationOptions
import org.pnfolder.pnlibrary.api.currency.CurrencyStorage
import org.pnfolder.pnlibrary.api.currency.ManagedCurrencyBuilder
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAccess
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderKey
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderPublication
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.math.BigDecimal
import java.time.Duration
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.function.Consumer

/** Common access and alias configuration shared by all currency builders. */
internal open class CurrencyOptions : CurrencyRegistrationOptions {
    var access: CurrencyAccess = CurrencyAccess.ownerOnly()
    val aliases = linkedSetOf<String>()

    override fun access(access: CurrencyAccess) = apply { this.access = access }
    override fun access(configure: Consumer<CurrencyAccess.Builder>) = apply {
        access = CurrencyAccess.builder().also(configure::accept).build()
    }
    override fun aliases(vararg aliases: String) = apply { this.aliases += aliases }
}

/** Assembles a lightweight callback-backed provider. */
internal class CurrencyDefinition(private val name: String) : CurrencyOptions(), CurrencyDefinitionBuilder {
    private val descriptor = CurrencyDescriptor.builder(name)
    private val operations = CurrencyOperationsBuilder()

    override fun descriptor(configure: Consumer<CurrencyDescriptor.Builder>) = apply { configure.accept(descriptor) }
    override fun operations(configure: Consumer<CurrencyOperations>) = apply { configure.accept(operations) }
    fun provider(): CurrencyProvider = operations.build(descriptor.build(), name)
}

/** Assembles a storage-backed provider and its optional placeholder publications. */
internal class ManagedCurrencyDefinition(
    private val owner: PluginId,
    private val name: String,
) : CurrencyOptions(), ManagedCurrencyBuilder {
    private val descriptor = CurrencyDescriptor.builder(name)
    private var storage: CurrencyStorage? = null
    private var ownsStorage = false
    private var service = owner.value
    private val placeholderOptions = ManagedPlaceholderOptions(owner.value)
    private val commandOptions = ManagedCommandOptions()

    override fun descriptor(configure: Consumer<CurrencyDescriptor.Builder>) = apply { configure.accept(descriptor) }
    override fun storage(storage: CurrencyStorage) = apply {
        this.storage = storage
        ownsStorage = false
    }
    override fun ownedStorage(storage: CurrencyStorage) = apply {
        this.storage = storage
        ownsStorage = true
    }
    override fun service(name: String) = apply {
        require(name.isNotBlank()) { "Managed currency service must not be blank" }
        service = name.trim()
    }
    override fun placeholders(configure: Consumer<CurrencyPlaceholderOptions>) = apply { configure.accept(placeholderOptions) }
    override fun commands(configure: Consumer<CurrencyCommandOptions>) = apply { configure.accept(commandOptions) }

    fun provider(): CurrencyProvider = ManagedCurrencyProvider(
        key = CurrencyKey(owner, normalizeCurrencyName(name)),
        descriptor = descriptor.build(),
        storage = storage ?: error("Managed currency $owner:$name requires storage"),
        service = service,
        ownsStorage = ownsStorage,
        commandSettings = commandOptions.build(),
    )

    fun installPlaceholders(currency: CurrencyRegistration, placeholders: PlaceholderService): List<AutoCloseable> {
        if (!placeholderOptions.enabled) return emptyList()
        val normalizedName = normalizeCurrencyName(name)
        val prefix = "currency.$normalizedName"

        fun register(key: String, resolver: (UUID?) -> CompletionStage<Any?>): AutoCloseable {
            return placeholders.register(PlaceholderKey.of("$prefix.$key", Any::class.java)) { builder ->
                builder.resolveAsync { request -> resolver(request.playerId) }
                    .access(placeholderOptions.access)
                if (placeholderOptions.placeholderApi) {
                    builder.publish(
                        PlaceholderPublication("placeholderapi", placeholderOptions.namespace, "${normalizedName}_$key"),
                    )
                }
            }
        }

        fun ofPlayer(transform: (BigDecimal) -> Any?): (UUID?) -> CompletionStage<Any?> = { playerId ->
            playerId?.let { currency.balance(it).thenApply(transform) } ?: CompletableFuture.completedFuture(null)
        }

        return listOf(
            register("balance", ofPlayer { it }),
            register("formatted", ofPlayer(currency::format)),
            register("symbol") { CompletableFuture.completedFuture(currency.descriptor.symbol) },
        )
    }
}

private class ManagedPlaceholderOptions(defaultNamespace: String) : CurrencyPlaceholderOptions {
    var enabled = true
    var access: PlaceholderAccess = PlaceholderAccess.ownerOnly()
    var placeholderApi = false
    var namespace = defaultNamespace

    override fun enabled(value: Boolean) = apply { enabled = value }
    override fun access(access: PlaceholderAccess) = apply { this.access = access }
    override fun placeholderApi(enabled: Boolean) = apply { placeholderApi = enabled }
    override fun placeholderApi(namespace: String) = apply {
        require(namespace.isNotBlank()) { "PlaceholderAPI namespace must not be blank" }
        placeholderApi = true
        this.namespace = namespace
    }
}

private class ManagedCommandOptions : CurrencyCommandOptions {
    private val defaults = CurrencyCommandSettings()
    private var enabled = defaults.enabled
    private var permissionPrefix = defaults.permissionPrefix
    private var prefix = defaults.prefix
    private var success = defaults.success
    private var failure = defaults.failure
    private var balance = defaults.balance
    private var historyEmpty = defaults.historyEmpty
    private var confirmationMode = defaults.confirmationMode
    private var confirmationTimeout = defaults.confirmationTimeout

    override fun enabled(value: Boolean) = apply { enabled = value }
    override fun permissionPrefix(value: String) = apply { permissionPrefix = value.trim().ifEmpty { null } }
    override fun prefix(value: String) = apply { prefix = value }
    override fun success(value: String) = apply { success = value }
    override fun failure(value: String) = apply { failure = value }
    override fun balance(value: String) = apply { balance = value }
    override fun historyEmpty(value: String) = apply { historyEmpty = value }
    override fun confirmation(mode: CurrencyConfirmationMode) = apply { confirmationMode = mode }
    override fun confirmationTimeout(value: Duration) = apply { confirmationTimeout = value }

    fun build() = CurrencyCommandSettings(
        enabled = enabled,
        permissionPrefix = permissionPrefix,
        prefix = prefix,
        success = success,
        failure = failure,
        balance = balance,
        historyEmpty = historyEmpty,
        confirmationMode = confirmationMode,
        confirmationTimeout = confirmationTimeout,
    )
}

internal fun normalizeCurrencyName(value: String): String = value.trim().lowercase(Locale.ROOT).also {
    require(it.matches(Regex("[a-z0-9_.-]+"))) { "Invalid currency name: $value" }
}
