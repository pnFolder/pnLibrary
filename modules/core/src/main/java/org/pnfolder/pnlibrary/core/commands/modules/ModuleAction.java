package org.pnfolder.pnlibrary.core.commands.modules;

import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.plugin.control.ModuleControl;

/** A switch of {@code /pn modules}: the command word, the button text and what it does to a module. */
enum ModuleAction {
    ENABLE("enable", "Включить", "Запустить модуль", ChatPalette.OK) {
        @Override
        CompletionStage<?> run(ModuleControl control) {
            return control.enable();
        }
    },
    DISABLE("disable", "Выключить", "Остановить до следующего запуска сервера", ChatPalette.DANGER) {
        @Override
        CompletionStage<?> run(ModuleControl control) {
            return control.disable();
        }
    },
    RESTART("restart", "Перезапустить", "Выключить и включить заново", ChatPalette.WARN) {
        @Override
        CompletionStage<?> run(ModuleControl control) {
            return control.restart();
        }
    },
    RELOAD("reload", "Перечитать", "Перечитать настройки модуля", ChatPalette.INFO) {
        @Override
        CompletionStage<?> run(ModuleControl control) {
            return control.reload();
        }
    };

    private final String word;
    private final String label;
    private final String hint;
    private final TextColor color;

    ModuleAction(String word, String label, String hint, TextColor color) {
        this.word = word;
        this.label = label;
        this.hint = hint;
        this.color = color;
    }

    String getWord() {
        return word;
    }

    String getLabel() {
        return label;
    }

    String getHint() {
        return hint;
    }

    TextColor getColor() {
        return color;
    }

    @NotNull
    abstract CompletionStage<?> run(ModuleControl control);
}
