package ru.privatenull.pnlibrary.spi.platform

import ru.privatenull.pnlibrary.api.platform.PlatformType
/** Stable identity information for a native platform adapter. */
interface PlatformIdentity {
    val type: PlatformType
    val id: String get() = type.id
    val implementationName: String get() = type.displayName
    val isProxy: Boolean get() = type.isProxy
    val isServer: Boolean get() = type.isServer
}
