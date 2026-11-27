package org.pnfolder.pnlibrary.core.menu.screens.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.api.config.ConfigApply;
import org.pnfolder.pnlibrary.api.config.ConfigEditException;
import org.pnfolder.pnlibrary.api.config.ConfigSetting;
import org.pnfolder.pnlibrary.api.config.ConfigSettingKind;
import org.pnfolder.pnlibrary.core.menu.session.ClickType;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.session.MenuSession;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.style.ConfigLook;
import org.pnfolder.pnlibrary.core.menu.style.ConfigNumbers;

/** What a click on a setting does: flips, rolls, steps or asks for a value, and writes it to the file. */
final class ConfigEdits extends MenuScreens {
    private static final String CONFIGS_EDIT = "pnlibrary.configs.edit";

    private final ConfigPickers pickers;

    ConfigEdits(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
        this.pickers = new ConfigPickers(session, routes);
    }

    void change(MenuViewer player, String id, ConfigSetting setting, ClickType how, Consumer<MenuViewer> here) {
        boolean numeric = setting.getKind() == ConfigSettingKind.INTEGER || setting.getKind() == ConfigSettingKind.DECIMAL;
        boolean shift = how == ClickType.SHIFT_LEFT || how == ClickType.SHIFT_RIGHT;
        if (setting.getKind() == ConfigSettingKind.TEXT) {
            allowed(player, CONFIGS_EDIT, () -> ask(player, id, setting));
        } else if (numeric && how == ClickType.MIDDLE) {
            allowed(player, CONFIGS_EDIT, () -> pickers.number(player, id, setting.getPath(), here, writer(id), (who, item) -> ask(who, id, item)));
        } else if (setting.getKind() == ConfigSettingKind.CHOICE && shift && setting.getChoices().size() > 2) {
            allowed(player, CONFIGS_EDIT, () -> pickers.choice(player, id, setting.getPath(), 0, here, writer(id)));
        } else {
            String value = next(setting, how);
            if (value != null) {
                allowed(player, CONFIGS_EDIT, () -> write(player, id, setting, value, here));
            }
        }
    }

    private SettingWriter writer(String id) {
        return (who, setting, value, after) -> write(who, id, setting, value, after);
    }

    private void ask(MenuViewer player, String id, ConfigSetting setting) {
        List<String> hint = new ArrayList<>();
        if (!setting.getFormat().trim().isEmpty()) {
            hint.add("Формат: " + setting.getFormat() + ".");
        }
        if (!setting.getExamples().isEmpty()) {
            hint.add("Например: " + String.join(", ", setting.getExamples()) + ".");
        }
        suggestCommand(player, ("Введите новое значение для «" + setting.getTitle() + "». " + String.join(" ", hint)).trim(),
            "/pn configs edit " + id + " " + setting.getPath() + " " + (setting.getValue() == null ? "" : setting.getValue()));
    }

    private void write(MenuViewer player, String id, ConfigSetting setting, String next, Consumer<MenuViewer> after) {
        host().async(() -> {
            String outcome = null;
            try {
                data().editConfig(id, setting.getPath(), next);
            } catch (ConfigEditException error) {
                outcome = error.getMessage();
            } catch (Exception error) {
                outcome = error.getMessage() != null ? error.getMessage() : "Не удалось записать файл";
            }
            String failure = outcome;
            host().sync(() -> {
                if (failure != null) {
                    tell(player, ChatTone.FAILURE, "Не изменено.", failure);
                } else if (setting.getApply() != ConfigApply.LIVE) {
                    tell(player, ChatTone.WARNING, "Сохранено: " + setting.getTitle() + " = " + next + ".",
                        "Заработает: " + ConfigLook.applyWord(setting.getApply()) + ".");
                }
                after.accept(player);
            });
        });
    }

    /** The value a click moves to, or null when the click does nothing. */
    private static @Nullable String next(ConfigSetting setting, ClickType how) {
        String current = setting.getValue();
        if (current == null) {
            return null;
        }
        boolean backwards = how == ClickType.RIGHT || how == ClickType.SHIFT_RIGHT;
        boolean big = how == ClickType.SHIFT_LEFT || how == ClickType.SHIFT_RIGHT;
        switch (setting.getKind()) {
            case BOOLEAN:
                return String.valueOf(!current.equals("true"));
            case CHOICE:
                int size = setting.getChoices().size();
                return setting.getChoices().get(Math.floorMod(setting.getChoices().indexOf(current) + (backwards ? -1 : 1), size));
            case INTEGER:
                try {
                    double step = step(setting, big, backwards);
                    return Long.toString(Math.round(clamp(Long.parseLong(current.trim()) + step, setting)));
                } catch (NumberFormatException notANumber) {
                    return null;
                }
            case DECIMAL:
                try {
                    double step = step(setting, big, backwards);
                    double sum = Math.round((Double.parseDouble(current.trim()) + step) * 1000.0) / 1000.0;
                    return ConfigLook.number(clamp(sum, setting));
                } catch (NumberFormatException notANumber) {
                    return null;
                }
            default:
                return null;
        }
    }

    private static double step(ConfigSetting setting, boolean big, boolean backwards) {
        return ConfigNumbers.ladder(setting).get(big ? 1 : 0).getAmount() * (backwards ? -1 : 1);
    }

    private static double clamp(double value, ConfigSetting setting) {
        double low = setting.getMin() == null ? -Double.MAX_VALUE : setting.getMin();
        double high = setting.getMax() == null ? Double.MAX_VALUE : setting.getMax();
        return Math.max(low, Math.min(value, high));
    }
}
