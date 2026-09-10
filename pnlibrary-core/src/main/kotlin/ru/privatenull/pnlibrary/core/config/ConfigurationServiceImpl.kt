package ru.privatenull.pnlibrary.core.config

import ru.privatenull.pnlibrary.api.config.*
import ru.privatenull.pnlibrary.core.config.yaml.AnnotatedYamlCodec
import ru.privatenull.pnlibrary.core.config.yaml.CodeFirstYaml
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import java.util.logging.Logger

internal class ConfigurationServiceImpl(private val platform: PlatformAdapter) : ConfigurationService, AutoCloseable {
    private val scopes = java.util.IdentityHashMap<Any, Scope>()
    private val closed = AtomicBoolean(false)

    override fun scope(owner: Any): ConfigScope = synchronized(scopes) {
        check(!closed.get()) { "Configuration service is closed" }
        scopes.getOrPut(owner) { Scope(owner, ownerDirectory(owner).toAbsolutePath().normalize(), ownerLogger(owner)) }
    }

    fun close(owner: Any) = synchronized(scopes) { scopes.remove(owner) }?.close() ?: Unit

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(scopes) { scopes.values.toList().also { scopes.clear() } }.forEach { it.close() }
    }

    private fun ownerDirectory(owner: Any): Path = try {
        val value = owner.javaClass.getMethod("getDataFolder").invoke(owner)
        (value as File).toPath()
    } catch (_: ReflectiveOperationException) {
        val id = platform.ownerDetails(owner)["id"] ?: owner.javaClass.simpleName
        Paths.get("plugins", id)
    }

    private fun ownerLogger(owner: Any): Logger = try {
        owner.javaClass.getMethod("getLogger").invoke(owner) as Logger
    } catch (_: Exception) {
        Logger.getLogger(platform.ownerDetails(owner)["id"] ?: owner.javaClass.name)
    }

    private inner class Scope(
        private val owner: Any,
        private val directory: Path,
        private val logger: Logger,
    ) : ConfigScope {
        private val handles = linkedSetOf<ManagedConfig<*>>()
        private val scopeClosed = AtomicBoolean(false)
        override val size: Int get() = synchronized(handles) { handles.size }

        override fun <T : Any> yaml(path: String, type: Class<T>, defaults: Supplier<T>): ManagedConfig<T> {
            check(!scopeClosed.get()) { "Configuration scope is closed" }
            val relative = Paths.get(path).normalize()
            require(!relative.isAbsolute && !relative.startsWith("..")) { "Configuration path must stay inside the plugin directory" }
            require(path.endsWith(".yml", true) || path.endsWith(".yaml", true)) { "Configuration file must use .yml or .yaml" }
            val target = directory.resolve(relative).normalize()
            require(target.startsWith(directory)) { "Configuration path escapes the plugin directory" }
            val codec = AnnotatedYamlCodec(type)
            val defaultValue = defaults.get() ?: error("Configuration defaults cannot be null")
            val handle = CodeFirstYaml(target.toFile(), defaultValue, codec, logger, ConfigValueValidator(codec::validate))
            val owned = OwnedConfig(target, handle)
            synchronized(handles) {
                check(handles.none { (it as? OwnedConfig<*>)?.file == target }) { "Configuration $path is already registered" }
                handles += owned
            }
            return owned
        }

        override fun loadAll() = snapshot().forEach { it.load() }
        override fun reloadAll() = snapshot().forEach { it.reload() }
        override fun saveAll() = snapshot().filter { it.isLoaded }.forEach { it.save() }
        override fun close() {
            if (!scopeClosed.compareAndSet(false, true)) return
            synchronized(handles) { handles.toList().also { handles.clear() } }.forEach { it.close() }
            synchronized(scopes) { scopes.remove(owner, this) }
        }
        private fun snapshot() = synchronized(handles) { handles.toList() }
    }

    private class OwnedConfig<T>(val file: Path, private val delegate: ManagedConfig<T>) : ManagedConfig<T> by delegate
}
