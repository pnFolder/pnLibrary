package org.pnfolder.pnlibrary.api.placeholders

import org.jetbrains.annotations.ApiStatus
import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument
import org.pnfolder.pnlibrary.api.placeholders.annotation.Format
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo
import org.pnfolder.pnlibrary.api.placeholders.info.FormatInfo
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter
import org.pnfolder.pnlibrary.api.registry.Registry
import org.pnfolder.pnlibrary.api.registry.Visibility
import java.util.UUID
import java.util.concurrent.CompletionStage

/**
 * The placeholders of one module: annotated classes in, text out. Everything registered here is released when the
 * module closes.
 *
 * An address is `{group.branch.argument.field|format|format}`. Calls may come from any thread; answers finish on
 * the thread the method that gave the value chose.
 */
@ApiStatus.NonExtendable
interface PlaceholderService : AutoCloseable {
    /**
     * Registers the classes marked with [Placeholders]. With [papi] set, they are also shown to PlaceholderAPI as
     * `%papi_group_branch...%`; without it nothing is published outside.
     */
    fun register(vararg holders: Any, papi: String?)

    /** Registers the classes marked with [Placeholders] without showing them to PlaceholderAPI. */
    fun register(vararg holders: Any) = register(*holders, papi = null)

    /**
     * Registers one placeholder without a class, for names that are known only while running (one per currency, one
     * per setting). [address] is the plain path, such as `shop.coins`. Close the returned handle to remove it.
     */
    fun value(address: String, description: String, papi: String?, visibility: Visibility, resolve: PlaceholderValue): AutoCloseable

    /** Like the call with a [Visibility], visible to everyone. */
    fun value(address: String, description: String = "", papi: String? = null, resolve: PlaceholderValue): AutoCloseable =
        value(address, description, papi, Visibility.ALL, resolve)

    /** Registers one placeholder without a class, with no description and without PlaceholderAPI. */
    fun value(address: String, resolve: PlaceholderValue): AutoCloseable = value(address, "", null, resolve)

    /** Registers the methods marked with [Format] of [holders] as formats of this module: `{value|name}`. */
    fun formats(vararg holders: Any)

    /**
     * Registers how text becomes a [type] for the arguments of this module: `@Argument` parameters of that type use it.
     * Close the returned handle to remove it; it goes away with the module too. A converter named on the parameter
     * (`@Argument(converter = ...)`) is used before this one.
     */
    fun <T : Any> converter(type: Class<T>, converter: ArgumentConverter<T>): AutoCloseable

    /**
     * The value of [address] (with or without the braces), or `null` when it is unknown or has no value. A name in
     * [values] wins over every registered placeholder: it is how a scenario or a command hands over its own data.
     */
    fun resolve(address: String, viewer: ViewerInfo = ViewerInfo.none(), values: Map<String, Any?> = emptyMap()): CompletionStage<Any?>

    /** The value of [address] for nobody in particular. */
    fun resolve(address: String): CompletionStage<Any?> = resolve(address, ViewerInfo.none(), emptyMap())

    /** The value of [address] for [viewer]. */
    fun resolve(address: String, viewer: ViewerInfo): CompletionStage<Any?> = resolve(address, viewer, emptyMap())

    /** Like the call with a [ViewerInfo], for a plain player id (or `null`). */
    fun resolve(address: String, playerId: UUID?, values: Map<String, Any?> = emptyMap()): CompletionStage<Any?> =
        resolve(address, ViewerInfo.of(playerId), values)

    /** Replaces every `{address}` of [text]; an address that gives nothing stays as written. Then PlaceholderAPI fills its own `%...%`. */
    fun render(text: String, viewer: ViewerInfo = ViewerInfo.none(), values: Map<String, Any?> = emptyMap()): CompletionStage<String>

    /** [text] filled in for nobody in particular. */
    fun render(text: String): CompletionStage<String> = render(text, ViewerInfo.none(), emptyMap())

    /** [text] filled in for [viewer]. */
    fun render(text: String, viewer: ViewerInfo): CompletionStage<String> = render(text, viewer, emptyMap())

    /** Like the call with a [ViewerInfo], for a plain player id (or `null`). */
    fun render(text: String, playerId: UUID?, values: Map<String, Any?> = emptyMap()): CompletionStage<String> =
        render(text, ViewerInfo.of(playerId), values)

    /** Whether [address] names a registered placeholder; nothing is evaluated. */
    fun contains(address: String): Boolean

    /** Every placeholder of the whole library, for lists and menus. */
    fun list(): List<PlaceholderInfo>

    /** The same placeholders as [list], as a [Registry]: found by address, with owner and visibility. */
    fun registry(): Registry<PlaceholderInfo>

    /** Every format that `{value|name}` can use, with the module that brought it. */
    fun formatRegistry(): Registry<FormatInfo>

    /** The outside placeholder systems (PlaceholderAPI) the library talks to. */
    fun adapters(): PlaceholderAdapterRegistry

    /** Releases everything this module registered. */
    override fun close()
}
