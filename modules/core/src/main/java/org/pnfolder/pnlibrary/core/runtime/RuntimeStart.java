package org.pnfolder.pnlibrary.core.runtime;

import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.core.commands.cooldowns.CooldownsCommand;
import org.pnfolder.pnlibrary.core.commands.downloads.DownloadsCommand;
import org.pnfolder.pnlibrary.core.commands.registry.LibraryRegistries;
import org.pnfolder.pnlibrary.core.commands.registry.RegistryCommand;
import org.pnfolder.pnlibrary.core.commands.settings.SettingsCommand;
import org.pnfolder.pnlibrary.core.diagnostics.command.DiagnosticCommands;
import org.pnfolder.pnlibrary.core.lifecycle.MountableCommands;
import org.pnfolder.pnlibrary.core.placeholders.library.LibraryPlaceholders;
import org.pnfolder.pnlibrary.core.placeholders.PlaceholdersCommand;
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeMetricsBootstrap;
import org.pnfolder.pnlibrary.core.scenario.ActionsCommand;

/** Starts the runtime parts and mounts the commands the platform's {@code /pn} control command offers. */
final class RuntimeStart {
    private final RuntimeParts parts;
    private final PnLibraryImpl library;

    RuntimeStart(RuntimeParts parts, PnLibraryImpl library) {
        this.parts = parts;
        this.library = library;
    }

    void run() {
        parts.support.initialize(library::isClosed);
        parts.setLibraryMetrics(new RuntimeMetricsBootstrap(parts.metricsService, parts.platform.getIdentity(), parts.owner,
                message -> parts.logging.logger(parts.owner, PnLibraryIdentity.NAME).warning(message)).start());
        parts.stats.start();
        mountCommands();
        parts.features.getPlaceholders().scope("pnlibrary/main").register(new LibraryPlaceholders(parts.platform.getIdentity(),
                parts.startedAtMillis, () -> parts.plugins.all().size(), parts.stats::values));
        library.registerRuntimeCommand(LibraryInfoCommand.create(library, parts.platform.getIdentity(),
                () -> orUnknown(parts.platform.getOwnership().ownerDetails(parts.owner).get("version"))));
    }

    private static String orUnknown(String version) {
        return version != null ? version : "неизвестна";
    }

    private void mountCommands() {
        PnLibrary self = library;
        parts.mountable.put(MountableCommands.DEBUG, DiagnosticCommands.create(self, "/pn debug"));
        parts.mountable.put(MountableCommands.EVENTS, parts.journalRuntime.command());
        parts.mountable.put(MountableCommands.STATS, parts.stats.command());
        parts.mountable.put(MountableCommands.PLACEHOLDERS, PlaceholdersCommand.create(parts.features.getPlaceholders()));
        parts.mountable.put(MountableCommands.ACTIONS, ActionsCommand.create(library::scenarioDefinitions));
        parts.mountable.put(MountableCommands.REGISTRY, RegistryCommand.create(LibraryRegistries.of(
                parts.features.getPlaceholders(), parts.features.getServices(), parts.features.getExpansions(),
                library::scenarioDefinitions)));
        parts.mountable.put(MountableCommands.COOLDOWNS, CooldownsCommand.create(() -> parts.plugins, library::getAudiences));
        parts.mountable.put(MountableCommands.DOWNLOADS, DownloadsCommand.create(() -> parts.plugins));
        parts.mountable.put(MountableCommands.SETTINGS, new SettingsCommand(parts.settings, parts.settingPicker).definition());
    }
}
