package org.pnfolder.pnlibrary.spi.platform

import org.pnfolder.pnlibrary.api.logging.LogLevel

/** Native plugin ownership and metadata operations used by the shared engine. */
interface PlatformOwnership : PlatformOwnerMetadata, PlatformPluginInventory, PlatformOwnerControl

/** Logs and returns the standard result for an owner unsupported by the native platform. */
fun unsupportedOwner(owner: Any, logging: PlatformLogging): PluginSnapshot? {
    logging.log(owner, LogLevel.WARNING,
        "Не удалось получить метаданные плагина: объект ${owner.javaClass.name} " +
            "не является поддерживаемым плагином. Операция пропущена; работа продолжается.")
    return null
}
