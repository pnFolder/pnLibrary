@file:JvmName("Services")

package ru.privatenull.pnlibrary.api.services

/** Returns the highest-priority service registered for reified type [T], if any. */
inline fun <reified T : Any> ServiceManager.get(): T? = get(T::class.java)

/** Returns the highest-priority service for [T], failing when none is registered. */
inline fun <reified T : Any> ServiceManager.require(): T = require(T::class.java)

/** Returns every service registered for [T] in descending priority order. */
inline fun <reified T : Any> ServiceManager.getAll(): List<T> = getAll(T::class.java)

/**
 * Registers [service] under its reified API type [T].
 *
 * Use an explicit class with [ServiceManager.register] when the implementation must
 * be published under an interface other than the inferred compile-time type.
 */
inline fun <reified T : Any> ServiceManager.register(
    service: T,
    priority: Int = 0,
) = register(T::class.java, service, priority)

/** Removes every service registration associated with reified type [T]. */
inline fun <reified T : Any> ServiceManager.unregister() = unregister(T::class.java)
