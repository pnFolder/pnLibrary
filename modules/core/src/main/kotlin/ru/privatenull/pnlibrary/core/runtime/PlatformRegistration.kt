package ru.privatenull.pnlibrary.core.runtime

/** Internal capability used by the runtime host to publish a native platform API. */
internal interface PlatformRegistration {
    fun <T : Any> registerPlatform(type: Class<T>, implementation: T): AutoCloseable
}
