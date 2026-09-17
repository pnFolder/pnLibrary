package ru.privatenull.pnlibrary.core.platform

import ru.privatenull.pnlibrary.api.platform.PlatformProvider
import java.util.concurrent.ConcurrentHashMap

/** Small runtime registry for the single native platform implementation. */
internal class PlatformProviderImpl : PlatformProvider, AutoCloseable {
    private val implementations = ConcurrentHashMap<Class<*>, Any>()

    override fun <T : Any> get(type: Class<T>): T? = implementations[type]?.let(type::cast)

    override fun <T : Any> require(type: Class<T>): T = get(type)
        ?: throw IllegalStateException("Platform API ${type.name} is not available in this runtime")

    fun <T : Any> register(type: Class<T>, implementation: T): AutoCloseable {
        require(implementations.putIfAbsent(type, implementation) == null) {
            "Platform API ${type.name} is already registered"
        }
        return AutoCloseable { implementations.remove(type, implementation) }
    }

    override fun close() {
        implementations.clear()
    }
}
