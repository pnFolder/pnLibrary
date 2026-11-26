package org.pnfolder.pnlibrary.core.menu.style;

import org.pnfolder.pnlibrary.api.config.ConfigSetting;

/** What a click on a setting does, as the hint lines of its button. */
final class ConfigHints {
    private static final int CHOICES_SHOWN = 8;

    private ConfigHints() {
    }

    static String[] of(ConfigSetting setting, boolean readOnly) {
        if (readOnly) {
            return new String[0];
        }
        switch (setting.getKind()) {
            case BOOLEAN:
                return new String[] {Style.left("включить или выключить")};
            case CHOICE:
                return setting.getChoices().size() > CHOICES_SHOWN
                    ? new String[] {Style.left("следующий"), Style.right("предыдущий"), Style.shiftLeft("весь список")}
                    : new String[] {Style.left("следующий вариант"), Style.right("предыдущий")};
            case INTEGER:
            case DECIMAL:
                return new String[] {Style.left("больше"), Style.right("меньше"), Style.shiftLeft("в десять раз сильнее"),
                    Style.middle("ввести число")};
            default:
                return new String[] {Style.left("ввести текст в чате")};
        }
    }
}
