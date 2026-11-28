package org.pnfolder.pnlibrary.kotlin

import org.pnfolder.pnlibrary.api.expansions.Expansion
import org.pnfolder.pnlibrary.api.expansions.ExpansionManager
import org.pnfolder.pnlibrary.api.platform.PlatformProvider
import org.pnfolder.pnlibrary.api.services.ServiceManager

/** The service of type [T], or `null`: `services.get<Economy>()` instead of `services.get(Economy::class.java)`. */
public inline fun <reified T : Any> ServiceManager.get(): T? = get(T::class.java)

/** The service of type [T]; throws [IllegalStateException] when none is registered. */
public inline fun <reified T : Any> ServiceManager.require(): T = require(T::class.java)

/** Every service of type [T], best first. */
public inline fun <reified T : Any> ServiceManager.all(): List<T> = all(T::class.java)

/** Registers [service] under its type [T] (not its class), so `get<T>()` finds it. */
public inline fun <reified T : Any> ServiceManager.register(service: T, priority: Int = 0): Unit =
    register(T::class.java, service, priority)

/** Removes the service registered under [T]. */
public inline fun <reified T : Any> ServiceManager.unregister(): Unit = unregister(T::class.java)

/** The platform object of type [T], or `null`: `platforms.get<org.bukkit.Server>()`. */
public inline fun <reified T : Any> PlatformProvider.get(): T? = get(T::class.java)

/** The platform object of type [T]; throws when the platform does not provide it. */
public inline fun <reified T : Any> PlatformProvider.require(): T = require(T::class.java)

/** The first registered expansion of type [T], or `null`. */
public inline fun <reified T : Expansion> ExpansionManager.get(): T? = get(T::class.java)
