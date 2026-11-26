package org.pnfolder.pnlibrary.bukkit.commands;

import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeBridge;
import org.pnfolder.pnlibrary.core.settings.RestartSettings;
import org.pnfolder.pnlibrary.core.settings.SettingsStore;

/** Reads the restart settings at the moment they are needed, so a change in {@code /pn settings} works at once. */
final class RestartSettingsSource {
    private RestartSettingsSource() {
    }

    /** The settings in force; the defaults when the library has no settings yet. */
    static RestartSettings of(PnLibrary library) {
        SettingsStore store = RuntimeBridge.librarySettings(library);
        return store == null ? new RestartSettings() : store.current().getRestart();
    }
}
