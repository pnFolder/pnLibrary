package org.pnfolder.pnlibrary.api.placeholders

/**
 * Validated placeholder identifier and the value type produced by its resolver.
 *
 * @property value lowercase key containing letters, digits, `_`, `.`, `-`, or braces
 * @property type runtime value type used for formatter lookup and Java interop
 */
data class PlaceholderKey<T : Any>(val value: String, val type: Class<T>) {
    init {
        require(value.matches(VALID_KEY)) { "Invalid placeholder key: $value" }
    }

    /** Java-friendly factory and validation rules for placeholder keys. */
    companion object {
        private val VALID_KEY = Regex("[a-z0-9_.{}-]+")

        /** Creates and validates a typed placeholder key. */
        @JvmStatic
        fun <T : Any> of(value: String, type: Class<T>): PlaceholderKey<T> = PlaceholderKey(value, type)
    }
}
