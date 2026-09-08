@file:JvmName("Services")

package ru.privatenull.pnlibrary.api.services

inline fun <reified T : Any> ServiceManager.get(): T? = get(T::class.java)
inline fun <reified T : Any> ServiceManager.require(): T = require(T::class.java)
inline fun <reified T : Any> ServiceManager.getAll(): List<T> = getAll(T::class.java)
inline fun <reified T : Any> ServiceScope.get(): T? = get(T::class.java)
inline fun <reified T : Any> ServiceScope.require(): T = require(T::class.java)
inline fun <reified T : Any> ServiceScope.getAll(): List<T> = getAll(T::class.java)
inline fun <reified T : Any> ServiceScope.publish(service: T, priority: Int = 0): ServiceRegistration<T> =
    publish(T::class.java, service, priority)
