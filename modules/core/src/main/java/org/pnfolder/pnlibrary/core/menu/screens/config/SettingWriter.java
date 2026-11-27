package org.pnfolder.pnlibrary.core.menu.screens.config;

import java.util.function.Consumer;
import org.pnfolder.pnlibrary.api.config.ConfigSetting;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;

/** Writes a text into a setting, then runs {@code after} with the viewer; the screens that edit a value share it. */
@FunctionalInterface
interface SettingWriter {
    void write(MenuViewer who, ConfigSetting setting, String value, Consumer<MenuViewer> after);
}
