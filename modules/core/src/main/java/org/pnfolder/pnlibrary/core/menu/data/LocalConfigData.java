package org.pnfolder.pnlibrary.core.menu.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.pnfolder.pnlibrary.api.config.ConfigEditResult;
import org.pnfolder.pnlibrary.api.config.ConfigId;
import org.pnfolder.pnlibrary.api.config.ConfigInfo;
import org.pnfolder.pnlibrary.api.config.ConfigSetting;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.menu.screens.ConfigRow;

/** The configuration-file facts of {@link LocalMenuData}. */
final class LocalConfigData {
    private LocalConfigData() {
    }

    static List<ConfigRow> rows(PnLibrary library) {
        List<ConfigRow> rows = new ArrayList<>();
        for (ConfigInfo info : library.getConfigurations().all()) {
            org.pnfolder.pnlibrary.api.config.ManagedConfig config = library.getConfigurations().get(info.getId());
            rows.add(new ConfigRow(info.getId().toString(), info.getModelType().getName(), info.isLoaded(), info.getReadOnly(),
                config == null ? 0 : config.getSettings().size(), info.getPendingApply()));
        }
        return rows;
    }

    static List<ConfigSetting> settings(PnLibrary library, ConfigId id) {
        org.pnfolder.pnlibrary.api.config.ManagedConfig config = library.getConfigurations().get(id);
        return config == null ? Collections.<ConfigSetting>emptyList() : config.getSettings();
    }

    static ConfigEditResult edit(PnLibrary library, String id, String path, String text) {
        org.pnfolder.pnlibrary.api.config.ManagedConfig config = library.getConfigurations().get(ConfigId.parse(id));
        if (config == null) {
            throw new IllegalArgumentException("Файла " + id + " нет");
        }
        return config.edit(path, text);
    }
}
