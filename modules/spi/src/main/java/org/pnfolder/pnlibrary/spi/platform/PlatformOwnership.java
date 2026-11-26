package org.pnfolder.pnlibrary.spi.platform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.LogLevel;

/** Native plugin ownership and metadata operations used by the shared engine. */
public interface PlatformOwnership extends PlatformOwnerMetadata, PlatformPluginInventory, PlatformOwnerControl {
    /** Logs and returns the standard result for an owner unsupported by the native platform. */
    @Nullable
    static PluginSnapshot unsupportedOwner(@NotNull Object owner, @NotNull PlatformLogging logging) {
        logging.log(owner, LogLevel.WARNING,
                "Не удалось получить метаданные плагина: объект " + owner.getClass().getName()
                        + " не является поддерживаемым плагином. Операция пропущена; работа продолжается.");
        return null;
    }
}
