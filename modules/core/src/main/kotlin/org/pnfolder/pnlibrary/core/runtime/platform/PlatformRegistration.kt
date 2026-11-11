package org.pnfolder.pnlibrary.core.runtime.platform

internal interface PlatformRegistration {
    fun <T : Any> registerPlatform(type: Class<T>, implementation: T): AutoCloseable
}
