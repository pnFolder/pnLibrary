package ru.privatenull.pnlibrary.api.platform

import ru.privatenull.pnlibrary.api.runtime.PnLibrary

/** Type-safe access to the native platform API implemented by the active runtime. */
interface PlatformProvider {
    /** Returns the registered implementation for [type], or `null` when unavailable. */
    fun <T : Any> get(type: Class<T>): T?

    /** Returns the registered implementation or fails with a descriptive message. */
    fun <T : Any> require(type: Class<T>): T
}

/** Returns the native platform API implemented by this runtime. */
inline fun <reified T : Any> PnLibrary.platform(): T = platforms.require(T::class.java)
