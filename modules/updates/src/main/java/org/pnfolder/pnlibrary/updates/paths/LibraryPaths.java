package org.pnfolder.pnlibrary.updates.paths;

import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

/**
 * Every location the library reads or writes under its plugin folder; its own state lives in {@link #getData()}.
 */
public final class LibraryPaths {
    private final Path pluginFolder;
    private final Path plugins;
    private final Path legacyConfig;
    private final Path legacyDownloadsConfig;
    private final Path localization;
    private final InfoFileNames help;
    private final InfoFileNames settingsOverview;
    private final InfoFileNames commandGuide;
    private final Path serverUpdateFolder;
    private final Path data;
    private final Path settings;
    private final Path updates;
    private final Path preferences;
    private final Path components;
    private final Path events;
    private final Path catalog;
    private final Path configurations;
    private final Path configurationRestores;
    private final Path transactions;
    private final Path exports;
    private final Path databases;
    private final Path handout;
    private final Path staging;
    private final Path installLock;
    private final Path journal;
    private final Path history;
    private final Path taskHistory;
    private final Path eventHistory;
    private final Path downloads;
    private final Path reports;
    private final Path observability;
    private final Path observations;
    private final Path attachments;
    private final Path diagnosticsHistory;
    private final Path uploadLedger;

    /** Creates the locations under {@code pluginFolder}, the plugin data folder {@code plugins/pnLibrary}. */
    public LibraryPaths(@NotNull Path pluginFolder) {
        this.pluginFolder = pluginFolder;
        this.plugins = requireParent(pluginFolder);
        this.legacyConfig = pluginFolder.resolve("config.yml");
        this.legacyDownloadsConfig = pluginFolder.resolve("downloads.yml");
        this.localization = pluginFolder.resolve("localization");
        this.help = new InfoFileNames("Помощь.txt", "HELP.txt");
        this.settingsOverview = new InfoFileNames("Настройки.txt", "SETTINGS.txt");
        this.commandGuide = new InfoFileNames("Команды.txt", "COMMANDS.txt");
        this.serverUpdateFolder = plugins.resolve("update");
        this.data = pluginFolder.resolve("data");
        this.settings = data.resolve("settings.json");
        this.updates = data.resolve("updates");
        this.preferences = updates.resolve("preferences.json");
        this.components = updates.resolve("components.json");
        this.events = updates.resolve("events");
        this.catalog = updates.resolve("catalog");
        this.configurations = updates.resolve("configurations");
        this.configurationRestores = updates.resolve("configuration-restores");
        this.transactions = updates.resolve("transactions");
        this.exports = updates.resolve("exports");
        this.databases = updates.resolve("databases");
        this.handout = updates.resolve("handout");
        this.staging = updates.resolve("staging");
        this.installLock = updates.resolve("install.lock");
        this.journal = data.resolve("journal");
        this.history = data.resolve("history");
        this.taskHistory = history.resolve("task-history.tsv");
        this.eventHistory = history.resolve("event-history.tsv");
        this.downloads = data.resolve("downloads");
        this.reports = data.resolve("reports");
        this.observability = data.resolve("observability");
        this.observations = observability.resolve("observations.jsonl");
        this.attachments = observability.resolve("attachments");
        this.diagnosticsHistory = data.resolve("diagnostics").resolve("history");
        this.uploadLedger = data.resolve("upload-ledger.json");
    }

    private static Path requireParent(Path pluginFolder) {
        Path parent = pluginFolder.toAbsolutePath().getParent();
        if (parent == null) throw new IllegalArgumentException("The plugin folder must have a parent folder");
        return parent;
    }

    /** The plugin data folder. */
    @NotNull
    public Path getPluginFolder() {
        return pluginFolder;
    }

    /** The server's plugins folder. */
    @NotNull
    public Path getPlugins() {
        return plugins;
    }

    /** Old settings file, read once by the settings import. */
    @NotNull
    public Path getLegacyConfig() {
        return legacyConfig;
    }

    /** Old downloads settings file, read once by the settings import. */
    @NotNull
    public Path getLegacyDownloadsConfig() {
        return legacyDownloadsConfig;
    }

    /** Translations; they stay beside the data folder. */
    @NotNull
    public Path getLocalization() {
        return localization;
    }

    /** Generated help for administrators in the plugin folder; rewritten at every start, edits are lost. */
    @NotNull
    public InfoFileNames getHelp() {
        return help;
    }

    /** Generated overview of every library setting with its current value; rewritten at start and at every change. */
    @NotNull
    public InfoFileNames getSettingsOverview() {
        return settingsOverview;
    }

    /** Generated list of every command and permission; rewritten at every start, edits are lost. */
    @NotNull
    public InfoFileNames getCommandGuide() {
        return commandGuide;
    }

    /** Where the server picks up prepared plugin jars on its next start. */
    @NotNull
    public Path getServerUpdateFolder() {
        return serverUpdateFolder;
    }

    /** Root of the library's own state. */
    @NotNull
    public Path getData() {
        return data;
    }

    /** Every setting an administrator changed, in one file; see the settings command and the menu. */
    @NotNull
    public Path getSettings() {
        return settings;
    }

    /** Update state. */
    @NotNull
    public Path getUpdates() {
        return updates;
    }

    /** Channel, automatic-download and freeze choices, in one file. */
    @NotNull
    public Path getPreferences() {
        return preferences;
    }

    /** Components known to the updater. */
    @NotNull
    public Path getComponents() {
        return components;
    }

    /** Update event journal. */
    @NotNull
    public Path getEvents() {
        return events;
    }

    /** Cached release catalogs. */
    @NotNull
    public Path getCatalog() {
        return catalog;
    }

    /** Configuration snapshots. */
    @NotNull
    public Path getConfigurations() {
        return configurations;
    }

    /** Configuration restore journals. */
    @NotNull
    public Path getConfigurationRestores() {
        return configurationRestores;
    }

    /** Update transaction journals. */
    @NotNull
    public Path getTransactions() {
        return transactions;
    }

    /** Exports saved before updates. */
    @NotNull
    public Path getExports() {
        return exports;
    }

    /** Database copies saved before updates. */
    @NotNull
    public Path getDatabases() {
        return databases;
    }

    /** Files handed to administrators. */
    @NotNull
    public Path getHandout() {
        return handout;
    }

    /** Downloaded files before installation. */
    @NotNull
    public Path getStaging() {
        return staging;
    }

    /** Lock held while an update is installed. */
    @NotNull
    public Path getInstallLock() {
        return installLock;
    }

    /** The journal of library events. */
    @NotNull
    public Path getJournal() {
        return journal;
    }

    /** Task and event history. */
    @NotNull
    public Path getHistory() {
        return history;
    }

    /** Task history file. */
    @NotNull
    public Path getTaskHistory() {
        return taskHistory;
    }

    /** Event history file. */
    @NotNull
    public Path getEventHistory() {
        return eventHistory;
    }

    /** Download state; the download classes take data and keep their files in this folder. */
    @NotNull
    public Path getDownloads() {
        return downloads;
    }

    /** Support reports. */
    @NotNull
    public Path getReports() {
        return reports;
    }

    /** Observability state. */
    @NotNull
    public Path getObservability() {
        return observability;
    }

    /** Recorded observations. */
    @NotNull
    public Path getObservations() {
        return observations;
    }

    /** Attachments of observations. */
    @NotNull
    public Path getAttachments() {
        return attachments;
    }

    /** Diagnostics history. */
    @NotNull
    public Path getDiagnosticsHistory() {
        return diagnosticsHistory;
    }

    /** Ledger of uploaded reports. */
    @NotNull
    public Path getUploadLedger() {
        return uploadLedger;
    }
}
