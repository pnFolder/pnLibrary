package org.pnfolder.pnlibrary.core.commands.configs;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.config.ConfigApply;
import org.pnfolder.pnlibrary.api.config.ConfigEditException;
import org.pnfolder.pnlibrary.api.config.ConfigEditResult;
import org.pnfolder.pnlibrary.api.config.ConfigId;
import org.pnfolder.pnlibrary.api.config.ConfigInfo;
import org.pnfolder.pnlibrary.api.config.ConfigSetting;
import org.pnfolder.pnlibrary.api.config.ManagedConfig;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;

/** The settings of a file that may be changed in game: listing them and changing one. */
final class ConfigSettingsPages {
    private static final String ROOT = "/pn configs";

    private final PnLibrary library;

    ConfigSettingsPages(PnLibrary library) {
        this.library = library;
    }

    private ManagedConfig<?> config(ConfigId id) {
        ManagedConfig<?> config = library.getConfigurations().get(id);
        if (config == null) {
            throw new IllegalStateException("Configuration " + id + " is gone");
        }
        return config;
    }

    private void say(CommandContext context, String text, TextColor color) {
        context.getSender().sendMessage(Component.text(text, color));
    }

    private void say(CommandContext context, String text) {
        say(context, text, ChatPalette.TEXT);
    }

    void show(CommandContext context, ConfigId id) {
        ManagedConfig<?> config = config(id);
        List<ConfigSetting> list = config.getSettings();
        if (list.isEmpty()) {
            say(context, id + ": нет настроек, которые можно менять в игре.", ChatPalette.FAINT);
            return;
        }
        say(context, id + ": настроек " + list.size(), ChatPalette.ACCENT);
        for (ConfigSetting setting : list) {
            say(context, settingLine(setting));
            if (!setting.getDescription().trim().isEmpty()) {
                say(context, "    " + setting.getTitle() + ": " + setting.getDescription(), ChatPalette.FAINT);
            }
        }
        ConfigInfo info = config.getInfo();
        if (info != null && info.getPendingApply() != null) {
            say(context, "Есть изменения, которые ещё не работают: " + applyWord(info.getPendingApply()) + ".", ChatPalette.WARN);
        }
    }


    private String settingLine(ConfigSetting setting) {
        List<String> limits = new ArrayList<>();
        if (setting.getMin() != null) {
            limits.add("от " + trim(setting.getMin()));
        }
        if (setting.getMax() != null) {
            limits.add("до " + trim(setting.getMax()));
        }
        String choices = setting.getChoices().isEmpty() ? "" : " (" + String.join("|", setting.getChoices()) + ")";
        String applies = setting.getApply() == ConfigApply.RELOAD ? "  [нужно перечитать]"
                : setting.getApply() == ConfigApply.RESTART ? "  [нужен перезапуск]" : "";
        String shown = limits.isEmpty() ? "" : " (" + String.join(" ", limits) + ")";
        return "  " + setting.getPath() + " = " + (setting.getValue() == null ? "?" : setting.getValue()) + choices + shown + applies;
    }


    private static String trim(double number) {
        return number == Math.floor(number) ? String.valueOf((long) number) : String.valueOf(number);
    }


    private String applyWord(ConfigApply apply) {
        switch (apply) {
            case LIVE:
                return "работают сразу";
            case RELOAD:
                return "нужно перечитать файл: " + ROOT + " reload <адрес>";
            default:
                return "нужен перезапуск сервера";
        }
    }


    void edit(CommandContext context, ConfigId id, String path, String value) {
        ManagedConfig<?> config = config(id);
        try {
            ConfigEditResult result = config.edit(path, value);
            say(context, id + ": " + path + " " + (result.getPrevious() == null ? "?" : result.getPrevious()) + " → "
                    + result.getSetting().getValue() + ". Файл сохранён.", ChatPalette.OK);
            if (result.getSetting().getApply() != ConfigApply.LIVE) {
                say(context, "Начнёт работать: " + applyWord(result.getSetting().getApply()) + ".", ChatPalette.WARN);
            }
        } catch (ConfigEditException | IllegalStateException refused) {
            say(context, "Не изменено: " + refused.getMessage(), ChatPalette.DANGER);
        }
    }
}
