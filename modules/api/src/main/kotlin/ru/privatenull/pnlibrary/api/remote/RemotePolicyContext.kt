package ru.privatenull.pnlibrary.api.remote

import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersionInfo
import ru.privatenull.pnlibrary.api.platform.PlatformType
import java.util.Collections
import java.util.Locale

/**
 * Identity of the product being evaluated.
 *
 * @property id stable machine-readable product identifier
 * @property name human-readable product name
 * @property version installed product version
 */
data class ProductInfo(val id: String, val name: String, val version: String)

/**
 * Runtime platform identity exposed to a remote policy.
 *
 * @property type normalized pnLibrary platform family
 * @property name platform implementation name
 * @property version platform implementation version
 */
data class PlatformInfo(
    val type: PlatformType,
    val name: String,
    val version: String,
) {
    /** Normalized alphanumeric implementation key used for tolerant comparisons. */
    val key: String = name.filter(Char::isLetterOrDigit).lowercase(Locale.ROOT)

    /** Returns whether the normalized platform name equals [value]. */
    fun isNamed(value: String): Boolean =
        key == value.filter(Char::isLetterOrDigit).lowercase(Locale.ROOT)
}

/**
 * Server and Minecraft protocol/version information exposed to a policy.
 *
 * @property version raw server-reported version string
 * @property minecraft parsed Minecraft version details
 */
data class ServerInfo(
    val version: String,
    val minecraft: MinecraftVersionInfo,
) {
    /** Parsed comparable Minecraft version. */
    val minecraftVersion: MinecraftVersion get() = minecraft.parsed
}

/**
 * One immutable context used by policies on every supported proxy/server platform.
 *
 * @property product product being evaluated
 * @property platform active proxy or server platform
 * @property server server and Minecraft version information
 * @property values application-defined immutable policy values
 */
class RemotePolicyContext private constructor(builder: Builder) {
    val product: ProductInfo = requireNotNull(builder.product)
    val platform: PlatformInfo = requireNotNull(builder.platform)
    val server: ServerInfo = requireNotNull(builder.server)
    val values: Map<String, String> = Collections.unmodifiableMap(HashMap(builder.values))
    private val nativeHandles: List<Any> = Collections.unmodifiableList(ArrayList(builder.nativeHandles))

    /** Returns the first native platform handle assignable to [type], if present. */
    fun <T : Any> nativeHandle(type: Class<T>): T? =
        nativeHandles.firstOrNull(type::isInstance)?.let(type::cast)

    /** Returns a native handle assignable to [type] or fails when unavailable. */
    fun <T : Any> requireNative(type: Class<T>): T =
        requireNotNull(nativeHandle(type)) { "native handle ${type.name} is unavailable on ${platform.type}" }

    /** Fluent builder for an immutable [RemotePolicyContext]. */
    class Builder {
        internal var product: ProductInfo? = null
        internal var platform: PlatformInfo? = null
        internal var server: ServerInfo? = null
        internal val values = linkedMapOf<String, String>()
        internal val nativeHandles = mutableListOf<Any>()
        /** Sets the product being evaluated. */
        fun product(value: ProductInfo) = apply { product = value }

        /** Sets the active platform. */
        fun platform(value: PlatformInfo) = apply { platform = value }

        /** Sets server and Minecraft version information. */
        fun server(value: ServerInfo) = apply { server = value }

        /** Adds or replaces one application-defined policy value. */
        fun value(key: String, value: String) = apply {
            val normalized = key.trim()
            require(normalized.isNotEmpty()) { "remote policy context value key must not be blank" }
            values[normalized] = value
        }
        /** Adds all application-defined policy values from [value]. */
        fun values(value: Map<String, String>) = apply {
            val normalized = value.mapKeys { (key, _) ->
                key.trim().also { require(it.isNotEmpty()) { "remote policy context value key must not be blank" } }
            }
            values.putAll(normalized)
        }
        /** Makes a platform-native object available for typed policy lookup. */
        fun nativeHandle(value: Any) = apply { nativeHandles += value }

        /** Validates required fields and creates the immutable context. */
        fun build() = RemotePolicyContext(this)
    }

    /** Creates context builders. */
    companion object {
        /** Returns an empty policy-context builder. */
        @JvmStatic
        fun builder() = Builder()
    }
}
