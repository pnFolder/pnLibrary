package ru.privatenull.pnlibrary.core.services

import ru.privatenull.pnlibrary.api.plugin.PluginId
import ru.privatenull.pnlibrary.api.services.ServiceManager
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Thread-safe implementation of the typed, plugin-owned service registry. */
internal class ServiceManagerImpl : ServiceManager, AutoCloseable {
    private val lock = Any()
    private val providers = hashMapOf<Class<*>, MutableList<Registration<*>>>()
    private val registrationsByOwner = hashMapOf<PluginId, MutableList<Registration<*>>>()
    private val sequence = AtomicLong()
    private val closed = AtomicBoolean(false)

    internal fun <T : Any> register(
        owner: PluginId,
        type: Class<T>,
        service: T,
        priority: Int = 0,
    ) = add(owner, type, service, priority)

    internal fun <T : Any> registerSystem(type: Class<T>, service: T, priority: Int = 0) =
        add(null, type, service, priority)

    private fun <T : Any> add(owner: PluginId?, type: Class<T>, service: T, priority: Int) {
        synchronized(lock) {
            check(!closed.get()) { "ServiceManager is closed" }
            require(type.isInstance(service)) { "${service.javaClass.name} does not implement ${type.name}" }
            val owned = owner?.let { registrationsByOwner.getOrPut(it) { mutableListOf() } }
            val duplicate = owned?.any { !it.isClosed && it.type == type }
                ?: providers[type]?.any { !it.isClosed && it.owner == null }
                ?: false
            check(!duplicate) {
                "${owner?.let { "Plugin $it" } ?: "pnLibrary runtime"} already provides service ${type.name}"
            }
            Registration(owner, type, service, priority, sequence.getAndIncrement()).also { registration ->
                owned?.add(registration)
                providers.getOrPut(type) { mutableListOf() } += registration
            }
        }
    }

    override fun <T : Any> get(type: Class<T>): T? = registrations(type).firstOrNull()?.service

    override fun <T : Any> getAll(type: Class<T>): List<T> = registrations(type).map { it.service }

    internal fun unregisterAll(owner: PluginId) {
        val registrations = synchronized(lock) { registrationsByOwner[owner]?.toList().orEmpty() }
        registrations.forEach { it.close() }
    }

    internal fun <T : Any> unregister(owner: PluginId, type: Class<T>) {
        val registration = synchronized(lock) {
            registrationsByOwner[owner]?.firstOrNull { !it.isClosed && it.type == type }
        }
        registration?.close()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val registrations = synchronized(lock) { providers.values.flatten() }
        registrations.forEach { it.close() }
        synchronized(lock) {
            providers.clear()
            registrationsByOwner.clear()
        }
    }

    private fun <T : Any> registrations(type: Class<T>): List<Registration<T>> {
        check(!closed.get()) { "ServiceManager is closed" }
        @Suppress("UNCHECKED_CAST")
        val current = synchronized(lock) { providers[type]?.toList().orEmpty() } as List<Registration<T>>
        return current.asSequence()
            .filterNot { it.isClosed }
            .sortedWith(compareByDescending<Registration<T>> { it.priority }.thenBy { it.order })
            .toList()
    }

    private inner class Registration<T : Any>(
        val owner: PluginId?,
        val type: Class<T>,
        val service: T,
        val priority: Int,
        val order: Long,
    ) : AutoCloseable {
        private val registrationClosed = AtomicBoolean(false)
        val isClosed: Boolean get() = registrationClosed.get()

        override fun close() {
            if (!registrationClosed.compareAndSet(false, true)) return
            synchronized(lock) {
                providers[type]?.let { entries ->
                    entries.remove(this)
                    if (entries.isEmpty()) providers.remove(type)
                }
                owner?.let { pluginId ->
                    registrationsByOwner[pluginId]?.let { entries ->
                        entries.remove(this)
                        if (entries.isEmpty()) registrationsByOwner.remove(pluginId)
                    }
                }
            }
        }
    }
}
