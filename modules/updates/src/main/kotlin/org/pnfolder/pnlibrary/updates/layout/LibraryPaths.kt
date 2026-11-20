package org.pnfolder.pnlibrary.updates.layout

import java.nio.file.Path

/**
 * The two names of a text file the library writes for administrators: [preferred] is in Russian, [ascii] is used when the
 * server's JVM cannot create a file with a Russian name (a non-UTF-8 `sun.jnu.encoding`).
 *
 * Only names, never [Path]s: building a path from [preferred] already fails on such a JVM, which must not stop the library.
 */
class InfoFileNames(val preferred: String, val ascii: String)

/**
 * Every location the library reads or writes under its plugin folder, defined in one place.
 *
 * The library's own state and its settings ([settings]) live in [data]; [localization] stays beside it, the two old
 * settings files ([legacyConfig], [legacyDownloadsConfig]) are only read once to import their values, and
 * [serverUpdateFolder] belongs to the server rather than to the library.
 *
 * @param pluginFolder the plugin data folder, `plugins/pnLibrary`
 */
class LibraryPaths(val pluginFolder: Path) {
    /** The server's plugins folder. */
    val plugins: Path = requireNotNull(pluginFolder.toAbsolutePath().parent) { "The plugin folder must have a parent folder" }

    // Read once by the settings import, or owned by the server: outside data.
    val legacyConfig: Path = pluginFolder.resolve("config.yml")
    val legacyDownloadsConfig: Path = pluginFolder.resolve("downloads.yml")
    val localization: Path = pluginFolder.resolve("localization")

    /** Generated help for administrators in the plugin folder; rewritten at every start, edits are lost. */
    val help = InfoFileNames("Помощь.txt", "HELP.txt")

    /** Generated overview of every library setting with its current value; rewritten at start and at every change. */
    val settingsOverview = InfoFileNames("Настройки.txt", "SETTINGS.txt")

    /** Generated list of every command and permission; rewritten at every start, edits are lost. */
    val commandGuide = InfoFileNames("Команды.txt", "COMMANDS.txt")

    /** Where the server picks up prepared plugin jars on its next start. */
    val serverUpdateFolder: Path = plugins.resolve("update")

    /** Root of the library's own state. */
    val data: Path = pluginFolder.resolve("data")

    /** Every setting an administrator changed, in one file; see the settings command and the menu. */
    val settings: Path = data.resolve("settings.json")

    // Updates.
    val updates: Path = data.resolve("updates")

    /** Channel, automatic-download and freeze choices, in one file. */
    val preferences: Path = updates.resolve("preferences.json")
    val components: Path = updates.resolve("components.json")
    val events: Path = updates.resolve("events")
    val catalog: Path = updates.resolve("catalog")
    val configurations: Path = updates.resolve("configurations")
    val configurationRestores: Path = updates.resolve("configuration-restores")
    val transactions: Path = updates.resolve("transactions")
    val exports: Path = updates.resolve("exports")
    val databases: Path = updates.resolve("databases")
    val handout: Path = updates.resolve("handout")
    val staging: Path = updates.resolve("staging")
    val installLock: Path = updates.resolve("install.lock")

    // Journal and history.
    val journal: Path = data.resolve("journal")
    val history: Path = data.resolve("history")
    val taskHistory: Path = history.resolve("task-history.tsv")
    val eventHistory: Path = history.resolve("event-history.tsv")

    /** Download state; the download classes take [data] and keep their files in this folder. */
    val downloads: Path = data.resolve("downloads")

    // Support.
    val reports: Path = data.resolve("reports")
    val observability: Path = data.resolve("observability")
    val observations: Path = observability.resolve("observations.jsonl")
    val attachments: Path = observability.resolve("attachments")
    val diagnosticsHistory: Path = data.resolve("diagnostics").resolve("history")
    val uploadLedger: Path = data.resolve("upload-ledger.json")
}
