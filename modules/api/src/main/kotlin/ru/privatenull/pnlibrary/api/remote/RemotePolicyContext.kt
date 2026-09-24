package ru.privatenull.pnlibrary.api.remote

import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersionInfo
import ru.privatenull.pnlibrary.api.platform.PlatformType
import java.util.Collections

data class ProductInfo(val id: String, val name: String, val version: String)

data class PlatformInfo(
    val type: PlatformType,
    val name: String,
    val version: String,
) {
    val key: String = name.filter(Char::isLetterOrDigit).lowercase()
    fun isNamed(value: String): Boolean = key == value.filter(Char::isLetterOrDigit).lowercase()
}

data class ServerInfo(
    val version: String,
    val minecraft: MinecraftVersionInfo,
) {
    val minecraftVersion: MinecraftVersion get() = minecraft.parsed
}

/** One immutable context used by policies on every supported proxy/server platform. */
class RemotePolicyContext private constructor(builder: Builder) {
    val product: ProductInfo = requireNotNull(builder.product)
    val platform: PlatformInfo = requireNotNull(builder.platform)
    val server: ServerInfo = requireNotNull(builder.server)
    val values: Map<String, String> = Collections.unmodifiableMap(HashMap(builder.values))
    private val nativeHandles: List<Any> = builder.nativeHandles.toList()

    fun <T : Any> nativeHandle(type: Class<T>): T? =
        nativeHandles.firstOrNull(type::isInstance)?.let(type::cast)

    fun <T : Any> requireNative(type: Class<T>): T =
        requireNotNull(nativeHandle(type)) { "native handle ${type.name} is unavailable on ${platform.type}" }

    class Builder {
        internal var product: ProductInfo? = null
        internal var platform: PlatformInfo? = null
        internal var server: ServerInfo? = null
        internal val values = linkedMapOf<String, String>()
        internal val nativeHandles = mutableListOf<Any>()
        fun product(value: ProductInfo) = apply { product = value }
        fun platform(value: PlatformInfo) = apply { platform = value }
        fun server(value: ServerInfo) = apply { server = value }
        fun value(key: String, value: String) = apply { values[key] = value }
        fun values(value: Map<String, String>) = apply { values.putAll(value) }
        fun nativeHandle(value: Any) = apply { nativeHandles += value }
        fun build() = RemotePolicyContext(this)
    }

    companion object { @JvmStatic fun builder() = Builder() }
}
