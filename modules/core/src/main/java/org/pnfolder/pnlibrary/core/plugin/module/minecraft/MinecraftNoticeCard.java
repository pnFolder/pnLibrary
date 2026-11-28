package org.pnfolder.pnlibrary.core.plugin.module.minecraft;

import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.api.compat.MinecraftSupport;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.console.ConsoleCard;
import org.pnfolder.pnlibrary.console.ConsoleTree;
import org.pnfolder.pnlibrary.core.text.ConsoleStyle;

/** The console card for a module that starts although the Minecraft version is not one it was checked on. */
public final class MinecraftNoticeCard {
    private MinecraftNoticeCard() {
    }

    /** The card for {@code module}, or null when the version is fine or the module did not start. */
    public static @Nullable ConsoleCard of(@NotNull String module, @NotNull Map<String, String> plugin, @NotNull MinecraftGate gate) {
        if (gate.reason() != null) {
            return null;
        }
        String name = plugin.containsKey("name") ? plugin.get("name") : "Плагин";
        String scope = (plugin.containsKey("id") ? plugin.get("id") : name) + ":" + module;
        boolean newer = gate.getSupport() == MinecraftSupport.NEWER_THAN_DECLARED;
        if (gate.isOverriding()) {
            return card("ЗАПУСК НА СВОЙ РИСК", "запущен вручную, версия не подходит", "Нужно: ", gate, plugin, module,
                ConsoleTree.builder("Что делать")
                    .child("Если плагин ведёт себя странно, отключите его")
                    .child("/pn modules mc force " + scope + " off").build(),
                "Плагин работает по вашему решению");
        }
        if (gate.getWarning() == null) {
            return null;
        }
        if (newer) {
            return card("НОВАЯ ВЕРСИЯ MINECRAFT", "запущен, но не проверен на этой версии", "Проверено: ", gate, plugin, module,
                ConsoleTree.builder("Что это значит")
                    .child("Плагин работает на ваш страх и риск")
                    .child("Возможны ошибки, которых автор не видел").build(),
                "Плагин работает, версия не проверена");
        }
        return card("СТАРАЯ ВЕРСИЯ MINECRAFT", "запущен на версии ниже нужной", "Нужно: ", gate, plugin, module,
            ConsoleTree.builder("Что это значит").child("Часть возможностей может не работать").build(),
            "Плагин работает, версия не поддерживается");
    }

    private static ConsoleCard card(String title, String subtitle, String declared, MinecraftGate gate, Map<String, String> plugin,
                                    String module, ConsoleTree advice, String status) {
        return ConsoleCard.builder(ConsoleStyle.theme(ChatTone.WARNING), title)
            .owner("o.o", plugin.containsKey("name") ? plugin.get("name") : "Плагин", module, plugin.get("version"), subtitle)
            .blank()
            .tree(ConsoleTree.builder("Версии")
                .child(declared + gate.getDeclared())
                .child("На сервере: " + gate.getServer())
                .build())
            .blank()
            .tree(advice)
            .support(PnLibraryIdentity.SUPPORT_URL)
            .blank()
            .status(status)
            .build();
    }
}
