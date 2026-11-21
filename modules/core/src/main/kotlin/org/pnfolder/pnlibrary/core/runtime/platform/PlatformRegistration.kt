package org.pnfolder.pnlibrary.core.runtime.platform

/** Registers a platform adapter implementation and returns a handle that unregisters it. */
internal interface PlatformRegistration {
    fun <T : Any> registerPlatform(type: Class<T>, implementation: T): AutoCloseable
}
