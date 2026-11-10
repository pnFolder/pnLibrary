package org.pnfolder.pnlibrary.core.runtime.details

import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry
import org.pnfolder.pnlibrary.api.expansions.Expansion
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.config.ConfigurationServiceImpl
import org.pnfolder.pnlibrary.core.downloads.DirectDownloads
import org.pnfolder.pnlibrary.core.downloads.DownloadConfiguration
import org.pnfolder.pnlibrary.core.expansions.ExpansionRegistry
import org.pnfolder.pnlibrary.core.expansions.LocalizationExpansion
import org.pnfolder.pnlibrary.core.placeholders.GlobalPlaceholderValueStore
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderHub
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformRuntimeContext
import org.pnfolder.pnlibrary.core.services.ServiceRegistry
import org.pnfolder.pnlibrary.core.ui.ConsoleStyle
import org.pnfolder.pnlibrary.core.updates.service.UpdateServiceImpl
import org.pnfolder.pnlibrary.currency.CurrencyFeature
import org.pnfolder.pnlibrary.currency.CurrencyRuntime
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import java.nio.file.Path

/** Creates the feature services of the library runtime (services, configs, updates and so on). */
internal class RuntimeFeatureSet(
    platform: PlatformRuntimeContext,
    dataFolder: Path,
    logging: PlatformLogging,
) {
    val services = ServiceRegistry()
    val configurations: ConfigurationServiceImpl = ConfigurationServiceImpl(platform.ownership, logging)
    val updates = UpdateServiceImpl(
        logging, platform.ownership, platform.ownership, platform.identity, platform.readiness, dataFolder,
        ConsoleStyle.THEME,
    )
    private val downloadConfiguration = DownloadConfiguration.load(dataFolder.resolve("downloads.yml")) {
        logging.log(this, LogLevel.WARNING, it)
    }
    val downloads: DirectDownloads = DirectDownloads(
        platform.identity, platform.ownership, platform.ownership, platform.readiness,
        logging, dataFolder, downloadConfiguration,
    )
    val placeholderValues = GlobalPlaceholderValueStore()
    val placeholders: PlaceholderHub = PlaceholderHub(
        platform.identity, placeholderValues,
        issues = { problem -> logging.log(this, LogLevel.WARNING, problem) },
        failures = { problem, error -> logging.log(this, LogLevel.ERROR, problem, error) },
    )
    val currency: CurrencyRuntime = CurrencyFeature { message, error -> logging.log(this, LogLevel.ERROR, message, error) }
    private val onExpansionFailure: (Expansion, Exception) -> Unit = { expansion, error ->
        logging.log(this, LogLevel.WARNING, "Expansion '${expansion.id}' failed: ${error.message}", error)
    }

    /** Expansions of plugins; the public `PnLibrary.expansions`. */
    val expansions = ExpansionRegistry(onExpansionFailure)

    /** The library's own expansions, which no plugin can see or control; modules reach them only through their context. */
    val libraryExpansions = ExpansionRegistry(onExpansionFailure)

    init {
        services.register(CurrencyProviderRegistry::class.java, currency.providers)
        // Item and entity names exist on game servers only; proxies get no loader and no cache folder.
        if (platform.identity.isServer) libraryExpansions.register(LocalizationExpansion(dataFolder.resolve("localization")))
    }
}
