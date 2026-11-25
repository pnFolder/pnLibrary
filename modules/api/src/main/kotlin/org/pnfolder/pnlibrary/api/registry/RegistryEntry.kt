package org.pnfolder.pnlibrary.api.registry

/**
 * One thing in a [Registry] as an administrator sees it in lists.
 *
 * @property key the name it is found by, such as `shop.coins`
 * @property value the registered thing itself
 * @property owner who registered it, as `plugin/module`, or `pnLibrary` for what the library brings
 * @property visibility who may use it
 * @property description what it is for, or an empty string
 */
data class RegistryEntry<T : Any>(
    val key: String,
    val value: T,
    val owner: String,
    val visibility: Visibility = Visibility.ALL,
    val description: String = "",
)
