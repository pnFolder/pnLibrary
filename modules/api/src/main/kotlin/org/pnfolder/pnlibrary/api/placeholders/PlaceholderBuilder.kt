package org.pnfolder.pnlibrary.api.placeholders

import java.util.function.Consumer

/** Fluent definition of one typed placeholder before it enters the service registry. */
interface PlaceholderBuilder<T : Any> {

    /** Selects a synchronous resolver, replacing any previously configured resolver. */
    fun resolve(resolver: PlaceholderResolver<T>): PlaceholderBuilder<T>

    /** Selects an asynchronous resolver, replacing any previously configured resolver. */
    fun resolveAsync(resolver: AsyncPlaceholderResolver<T>): PlaceholderBuilder<T>

    /** Adds an owner-defined update operation, making this placeholder writable. */
    fun update(updater: PlaceholderUpdater<T>): PlaceholderBuilder<T>

    /** Sets the access policy used only for update operations. */
    fun updateAccess(access: PlaceholderAccess): PlaceholderBuilder<T>

    /** Sets the complete consumer access policy. */
    fun access(access: PlaceholderAccess): PlaceholderBuilder<T>

    /** Builds and sets a consumer access policy with the Java-friendly callback. */
    fun access(configure: Consumer<PlaceholderAccess.Builder>): PlaceholderBuilder<T>

    /** Configures result caching for this placeholder. */
    fun cache(policy: PlaceholderCachePolicy): PlaceholderBuilder<T>

    /** Sets the value returned for null resolver results and while disabled. */
    fun fallback(value: T): PlaceholderBuilder<T>

    /**
     * Adds a format that only this placeholder understands: `{money|short}` calls [formatter] with the typed value.
     *
     * [name] uses lowercase letters, digits, `_` and `-`, and may be used once per placeholder. A format of this
     * placeholder wins over a plugin-wide or built-in one with the same name. It runs only while the value is still
     * a [T]; after an earlier format in the chain turned it into text, it is skipped.
     */
    fun format(name: String, formatter: PlaceholderFormatter<T>): PlaceholderBuilder<T>

    /** Requests publication through an external placeholder adapter. */
    fun publish(publication: PlaceholderPublication): PlaceholderBuilder<T>

    /** Publishes with the default PlaceholderAPI namespace and name. */
    fun publishToPlaceholderApi(): PlaceholderBuilder<T> =
        publish(PlaceholderPublication("placeholderapi"))

    /** Publishes through PlaceholderAPI under [namespace]. */
    fun publishToPlaceholderApi(namespace: String): PlaceholderBuilder<T> =
        publish(PlaceholderPublication("placeholderapi", namespace))

    /** Publishes through PlaceholderAPI under explicit [namespace] and [name]. */
    fun publishToPlaceholderApi(namespace: String, name: String): PlaceholderBuilder<T> =
        publish(PlaceholderPublication("placeholderapi", namespace, name))

}
