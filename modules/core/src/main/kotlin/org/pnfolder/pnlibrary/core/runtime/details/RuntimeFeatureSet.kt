package org.pnfolder.pnlibrary.core.runtime.details

import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.config.ConfigurationServiceImpl
import org.pnfolder.pnlibrary.core.downloads.DirectDownloadManager
import org.pnfolder.pnlibrary.core.downloads.DownloadConfiguration
import org.pnfolder.pnlibrary.core.placeholders.GlobalPlaceholderValueStore
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderHub
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformRuntimeContext
import org.pnfolder.pnlibrary.core.services.ServiceManagerImpl
import org.pnfolder.pnlibrary.core.updates.service.UpdateServiceImpl
import org.pnfolder.pnlibrary.currency.CurrencyFeature
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import java.nio.file.Path

/** Composes bundled features and their shared registrations independently of the runtime facade. */
internal class RuntimeFeatureSet(
    platform: PlatformRuntimeContext,
    dataFolder: Path,
    logging: PlatformLogging,
) {
    val services = ServiceManagerImpl()
    val configurations = ConfigurationServiceImpl(platform.ownership)
    val updates = UpdateServiceImpl(
        logging, platform.ownership, platform.ownership, platform.identity, platform.readiness, dataFolder,
    )
    private val downloadConfiguration = DownloadConfiguration.load(dataFolder.resolve("downloads.yml")) {
        logging.log(this, LogLevel.WARNING, it)
    }
    val downloads = DirectDownloadManager(
        platform.identity, platform.ownership, platform.ownership, platform.readiness,
        logging, dataFolder, downloadConfiguration,
    )
    val placeholderValues = GlobalPlaceholderValueStore()
    val placeholders = PlaceholderHub(platform.identity, placeholderValues)
    val currency = CurrencyFeature()

    init {
        services.register(CurrencyProviderRegistry::class.java, currency.providers)
    }
}
