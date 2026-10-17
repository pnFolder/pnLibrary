package ru.privatenull.pnlibrary.api.updates

import java.util.Locale

/**
 * Stable normalized identity of a pnLibrary-managed product.
 *
 * @property value lowercase validated product identifier
 */
class ProductId private constructor(val value: String) : Comparable<ProductId> {

    /** Compares product identifiers lexicographically. */
    override fun compareTo(other: ProductId): Int = value.compareTo(other.value)

    /** Returns whether [other] represents the same normalized identifier. */
    override fun equals(other: Any?): Boolean = other is ProductId && value == other.value

    /** Returns the normalized identifier hash. */
    override fun hashCode(): Int = value.hashCode()

    /** Returns [value] without additional formatting. */
    override fun toString(): String = value

    /** Creates validated product identifiers. */
    companion object {
        private val VALID = Regex("[a-z0-9][a-z0-9_.-]*")

        /** Normalizes and validates [value]. */
        @JvmStatic
        fun of(value: String): ProductId {
            val normalized = value.trim().lowercase(Locale.ROOT)
            require(VALID.matches(normalized)) { "Invalid product ID: $value" }
            return ProductId(normalized)
        }
    }
}
