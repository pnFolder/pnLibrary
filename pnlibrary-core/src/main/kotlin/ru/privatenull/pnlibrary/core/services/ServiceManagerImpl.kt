package ru.privatenull.pnlibrary.core.services

import ru.privatenull.pnlibrary.api.plugin.PluginId
import ru.privatenull.pnlibrary.api.services.ServiceManager
import ru.privatenull.pnlibrary.api.services.ServiceRegistration
import ru.privatenull.pnlibrary.api.services.ServiceScope
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Thread-safe implementation of the typed, owner-bound service registry. */
internal class ServiceManagerImpl : ServiceManager {
    private val scopes = hashMapOf<PluginId, Scope>()
    private val providers = hashMapOf<Class<*>, CopyOnWriteArrayList<Registration<*>>>()
    private val sequence = AtomicLong()
    private val closed = AtomicBoolean(false)

    override fun scope(owner: PluginId): ServiceScope = synchronized(scopes) {
        check(!closed.get()) { "ServiceManager is closed" }
        scopes.getOrPut(owner) { Scope(owner) }
    }

    override fun <T : Any> get(type: Class<T>): T? = registrations(type).firstOrNull()?.service

    override fun <T : Any> getAll(type: Class<T>): List<T> = registrations(type).map { it.service }

    override fun unregisterAll(owner: PluginId) {
        synchronized(scopes) { scopes.remove(owner) }?.closeInternal()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val current = synchronized(scopes) { scopes.values.toList().also { scopes.clear() } }
        current.forEach { it.closeInternal() }
        synchronized(providers) { providers.clear() }
    }

    private fun <T : Any> registrations(type: Class<T>): List<Registration<T>> {
        check(!closed.get()) { "ServiceManager is closed" }
        @Suppress("UNCHECKED_CAST")
        val current = synchronized(providers) { providers[type]?.toList().orEmpty() } as List<Registration<T>>
        return current.asSequence()
            .filterNot { it.isClosed }
            .sortedWith(compareByDescending<Registration<T>> { it.priority }.thenBy { it.order })
            .toList()
    }

    private inner class Scope(override val owner: PluginId) : ServiceScope {
        private val registrations = CopyOnWriteArrayList<Registration<*>>()
        private val scopeClosed = AtomicBoolean(false)
        override val isClosed: Boolean get() = scopeClosed.get()

        override fun <T : Any> publish(type: Class<T>, service: T, priority: Int): ServiceRegistration<T> {
            check(!closed.get() && !scopeClosed.get()) { "ServiceScope is closed" }
            require(type.isInstance(service)) { "${service.javaClass.name} does not implement ${type.name}" }
            synchronized(providers) {
                check(registrations.none { !it.isClosed && it.type == type }) {
                    "Plugin $owner already provides service ${type.name}"
                }
                val registration = Registration(this, type, service, priority, sequence.getAndIncrement())
                registrations += registration
                providers.getOrPut(type) { CopyOnWriteArrayList() } += registration
                return registration
            }
        }

        override fun <T : Any> get(type: Class<T>): T? = this@ServiceManagerImpl.get(type)
        override fun <T : Any> getAll(type: Class<T>): List<T> = this@ServiceManagerImpl.getAll(type)

        override fun close() {
            closeInternal()
            synchronized(scopes) { if (scopes[owner] === this) scopes.remove(owner) }
        }

        fun closeInternal() {
            if (!scopeClosed.compareAndSet(false, true)) return
            registrations.forEach { it.close() }
            registrations.clear()
        }

        fun remove(registration: Registration<*>) {
            registrations.remove(registration)
        }
    }

    private inner class Registration<T : Any>(
        private val scope: Scope,
        override val type: Class<T>,
        override val service: T,
        override val priority: Int,
        val order: Long,
    ) : ServiceRegistration<T> {
        private val registrationClosed = AtomicBoolean(false)
        override val owner: PluginId get() = scope.owner
        override val isClosed: Boolean get() = registrationClosed.get()

        override fun close() {
            if (!registrationClosed.compareAndSet(false, true)) return
            synchronized(providers) {
                providers[type]?.let { entries ->
                    entries.remove(this)
                    if (entries.isEmpty()) providers.remove(type)
                }
            }
            scope.remove(this)
        }
    }
}
