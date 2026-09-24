package ru.privatenull.pnlibrary.currency

import ru.privatenull.pnlibrary.api.currency.CurrencyAccess
import ru.privatenull.pnlibrary.api.currency.CurrencyCommandOptions
import ru.privatenull.pnlibrary.api.currency.CurrencyCommandSettings
import ru.privatenull.pnlibrary.api.currency.CurrencyConfirmationMode
import ru.privatenull.pnlibrary.api.currency.CurrencyDefinitionBuilder
import ru.privatenull.pnlibrary.api.currency.CurrencyDescriptor
import ru.privatenull.pnlibrary.api.currency.CurrencyOperations
import ru.privatenull.pnlibrary.api.currency.CurrencyPlaceholderOptions
import ru.privatenull.pnlibrary.api.currency.CurrencyProvider
import ru.privatenull.pnlibrary.api.currency.CurrencyRegistration
import ru.privatenull.pnlibrary.api.currency.CurrencyRegistrationOptions
import ru.privatenull.pnlibrary.api.currency.CurrencyStorage
import ru.privatenull.pnlibrary.api.currency.ManagedCurrencyBuilder
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderAccess
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderKey
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderPublication
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderService
import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.util.UUID
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
    private val descriptor = CurrencyDescriptor.Builder(name)
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
    private val descriptor = CurrencyDescriptor.Builder(name)
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
        key = ru.privatenull.pnlibrary.api.currency.CurrencyKey(owner, normalizeCurrencyName(name)),
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

        fun register(key: String, resolver: (UUID?) -> Any?): AutoCloseable {
            val builder = placeholders.placeholder(PlaceholderKey.of("$prefix.$key", Any::class.java))
                .resolve { request -> resolver(request.playerId) }
                .access(placeholderOptions.access)
            if (placeholderOptions.placeholderApi) {
                builder.publish(
                    PlaceholderPublication("placeholderapi", placeholderOptions.namespace, "${normalizedName}_$key"),
                )
            }
            return builder.register()
        }

        return listOf(
            register("balance") { playerId -> playerId?.let { currency.balance(it).toCompletableFuture().join() } },
            register("formatted") { playerId ->
                playerId?.let { currency.format(currency.balance(it).toCompletableFuture().join()) }
            },
            register("symbol") { currency.descriptor.symbol },
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
    private var enabled = true
    private var permissionPrefix: String? = null
    private var prefix = "§8[§6{currency}§8] "
    private var success = "§aOperation completed: {amount}"
    private var failure = "§c{error}"
    private var balance = "§fBalance: §a{balance}"
    private var historyEmpty = "§7No transactions found."
    private var confirmationMode = CurrencyConfirmationMode.CONSOLE
    private var confirmationTimeoutSeconds = 60

    override fun enabled(value: Boolean) = apply { enabled = value }
    override fun permissionPrefix(value: String) = apply { permissionPrefix = value.trim().ifEmpty { null } }
    override fun prefix(value: String) = apply { prefix = value }
    override fun success(value: String) = apply { success = value }
    override fun failure(value: String) = apply { failure = value }
    override fun balance(value: String) = apply { balance = value }
    override fun historyEmpty(value: String) = apply { historyEmpty = value }
    override fun confirmation(mode: CurrencyConfirmationMode) = apply { confirmationMode = mode }
    override fun confirmationTimeoutSeconds(value: Int) = apply {
        require(value in 10..600) { "Currency confirmation timeout must be between 10 and 600 seconds" }
        confirmationTimeoutSeconds = value
    }

    fun build() = CurrencyCommandSettings(
        enabled = enabled,
        permissionPrefix = permissionPrefix,
        prefix = prefix,
        success = success,
        failure = failure,
        balance = balance,
        historyEmpty = historyEmpty,
        confirmationMode = confirmationMode,
        confirmationTimeoutSeconds = confirmationTimeoutSeconds,
    )
}

internal fun normalizeCurrencyName(value: String): String = value.trim().lowercase().also {
    require(it.matches(Regex("[a-z0-9_.-]+"))) { "Invalid currency name: $value" }
}
