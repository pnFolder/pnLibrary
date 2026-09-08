@file:JvmName("Services")

package ru.privatenull.pnlibrary.api.services

inline fun <reified T : Any> ServiceManager.get(): T? = get(T::class.java)
inline fun <reified T : Any> ServiceManager.require(): T = require(T::class.java)
inline fun <reified T : Any> ServiceManager.getAll(): List<T> = getAll(T::class.java)

inline fun <reified T : Any> ServiceManager.register(
    service: T,
    priority: Int = 0,
) = register(T::class.java, service, priority)

inline fun <reified T : Any> ServiceManager.unregister() = unregister(T::class.java)
