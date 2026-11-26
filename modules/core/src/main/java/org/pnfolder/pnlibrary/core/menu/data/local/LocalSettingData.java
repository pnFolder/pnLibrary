package org.pnfolder.pnlibrary.core.menu.data.local;

import org.pnfolder.pnlibrary.core.menu.data.SettingRow;
import org.pnfolder.pnlibrary.core.menu.data.SettingOutcome;
import java.util.ArrayList;
import java.util.List;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeBridge;
import org.pnfolder.pnlibrary.core.settings.SettingApply;
import org.pnfolder.pnlibrary.core.settings.SettingChange;
import org.pnfolder.pnlibrary.core.settings.SettingException;
import org.pnfolder.pnlibrary.core.settings.SettingSpec;
import org.pnfolder.pnlibrary.core.settings.SettingValue;
import org.pnfolder.pnlibrary.core.settings.SettingsStore;

/** The library-setting facts of {@link LocalMenuData}. */
final class LocalSettingData {
    private final PnLibrary library;

    LocalSettingData(PnLibrary library) {
        this.library = library;
    }

    List<SettingRow> rows() {
        SettingsStore store = RuntimeBridge.librarySettings(library);
        List<SettingRow> rows = new ArrayList<>();
        if (store == null) {
            return rows;
        }
        for (SettingValue entry : store.values()) {
            SettingSpec spec = entry.getSpec();
            rows.add(new SettingRow(spec.getKey(), spec.getTitle(), spec.getKind().name(), spec.getDescription(),
                entry.getCurrent(), spec.getDefault(), entry.getChanged(), spec.range(), spec.getExamples(),
                spec.getApplies() == SettingApply.LIVE, spec.getChoices() == null ? "" : spec.getChoices().getId()));
        }
        return rows;
    }

    SettingOutcome change(String key, String value) {
        SettingsStore store = RuntimeBridge.librarySettings(library);
        if (store == null) {
            return new SettingOutcome(false, "Настройки здесь недоступны.");
        }
        try {
            SettingChange change = value == null ? store.reset(key) : store.set(key, value);
            return new SettingOutcome(true, change.getSpec().display(change.getAfter()),
                change.getSpec().getApplies() == SettingApply.LIVE);
        } catch (SettingException error) {
            return new SettingOutcome(false, error.getMessage() == null ? "" : error.getMessage());
        }
    }
}
