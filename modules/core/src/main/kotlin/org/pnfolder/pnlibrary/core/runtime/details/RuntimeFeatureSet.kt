package org.pnfolder.pnlibrary.core.runtime.details

import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry
import org.pnfolder.pnlibrary.api.expansions.Expansion
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.config.ConfigurationRuntime
import org.pnfolder.pnlibrary.config.ConfigurationServiceImpl
import org.pnfolder.pnlibrary.core.downloads.DirectDownloadManager
import org.pnfolder.pnlibrary.core.downloads.DownloadConfiguration
import org.pnfolder.pnlibrary.core.downloads.DownloadService
import org.pnfolder.pnlibrary.core.expansions.ExpansionManagerImpl
import org.pnfolder.pnlibrary.core.expansions.LocalizationExpansion
import org.pnfolder.pnlibrary.core.placeholders.GlobalPlaceholderValueStore
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderHub
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderRuntime
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformRuntimeContext
import org.pnfolder.pnlibrary.core.services.ServiceManagerImpl
import org.pnfolder.pnlibrary.core.ui.ConsoleStyle
import org.pnfolder.pnlibrary.core.updates.service.UpdateServiceImpl
import org.pnfolder.pnlibrary.currency.CurrencyFeature
import org.pnfolder.pnlibrary.currency.CurrencyRuntime
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import java.nio.file.Path

/** Composes bundled features and their shared registrations independently of the runtime facade. */
internal class RuntimeFeatureSet(
    platform: PlatformRuntimeContext,
    dataFolder: Path,
    logging: PlatformLogging,
) {
    val services = ServiceManagerImpl()
    val configurations: ConfigurationRuntime = ConfigurationServiceImpl(platform.ownership)
    val updates = UpdateServiceImpl(
        logging, platform.ownership, platform.ownership, platform.identity, platform.readiness, dataFolder,
        ConsoleStyle.THEME,
    )
    private val downloadConfiguration = DownloadConfiguration.load(dataFolder.resolve("downloads.yml")) {
        logging.log(this, LogLevel.WARNING, it)
    }
    val downloads: DownloadService = DirectDownloadManager(
        platform.identity, platform.ownership, platform.ownership, platform.readiness,
        logging, dataFolder, downloadConfiguration,
    )
    val placeholderValues = GlobalPlaceholderValueStore()
    val placeholders: PlaceholderRuntime = PlaceholderHub(platform.identity, placeholderValues)
    val currency: CurrencyRuntime = CurrencyFeature()
    private val onExpansionFailure: (Expansion, Exception) -> Unit = { expansion, error ->
        logging.log(this, LogLevel.WARNING, "Expansion '${expansion.id}' failed: ${error.message}", error)
    }

    /** Expansions of plugins; the public `PnLibrary.expansions`. */
    val expansions = ExpansionManagerImpl(onExpansionFailure)

    /** The library's own expansions, which no plugin can see or control; modules reach them only through their context. */
    val libraryExpansions = ExpansionManagerImpl(onExpansionFailure)

    init {
        services.register(CurrencyProviderRegistry::class.java, currency.providers)
        // Item and entity names exist on game servers only; proxies get no loader and no cache folder.
        if (platform.identity.isServer) libraryExpansions.register(LocalizationExpansion(dataFolder.resolve("localization")))
    }
}
