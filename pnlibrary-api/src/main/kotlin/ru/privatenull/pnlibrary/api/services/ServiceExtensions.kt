@file:JvmName("Services")

package ru.privatenull.pnlibrary.api.services

import ru.privatenull.pnlibrary.api.plugin.PluginContext

inline fun <reified T : Any> ServiceManager.get(): T? = get(T::class.java)
inline fun <reified T : Any> ServiceManager.require(): T = require(T::class.java)
inline fun <reified T : Any> ServiceManager.getAll(): List<T> = getAll(T::class.java)

/** Registers a service owned by this plugin context. */
inline fun <reified T : Any> PluginContext.registerService(
    service: T,
    priority: Int = 0,
) = registerService(T::class.java, service, priority)

/** Removes the service of this type registered by the plugin context. */
inline fun <reified T : Any> PluginContext.unregisterService() = unregisterService(T::class.java)
