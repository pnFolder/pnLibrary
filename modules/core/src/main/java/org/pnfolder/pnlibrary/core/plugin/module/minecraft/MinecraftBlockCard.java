package org.pnfolder.pnlibrary.core.plugin.module.minecraft;

import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.console.ConsoleCard;
import org.pnfolder.pnlibrary.console.ConsoleTree;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.core.text.ConsoleStyle;

/** The console card that says why a module was not started on this Minecraft version, in the library's console design. */
public final class MinecraftBlockCard {
    private MinecraftBlockCard() {
    }

    /**
     * The card for the module {@code module} of the plugin described by {@code plugin} (name, version, id, as
     * {@code PlatformOwnership.ownerDetails} gives them) that {@code gate} stopped.
     */
    public static @NotNull ConsoleCard of(@NotNull String module, @NotNull Map<String, String> plugin, @NotNull MinecraftGate gate) {
        String name = plugin.containsKey("name") ? plugin.get("name") : "Плагин";
        String version = plugin.get("version");
        String scope = (plugin.containsKey("id") ? plugin.get("id") : name) + ":" + module;
        ConsoleTree.Builder way = ConsoleTree.builder("Что делать");
        if (gate.isForceAllowed()) {
            way.child("Обновить сервер или плагин")
                .branch("Или запустить всё равно, на свой риск", force -> force
                    .child("/pn modules mc force " + scope + " on")
                    .child("Плагин может работать неверно"));
        } else {
            way.child("Обновить сервер или плагин")
                .child("Запуск вручную запретил автор плагина");
        }
        return ConsoleCard.builder(ConsoleStyle.theme(ChatTone.FAILURE), "ВЕРСИЯ MINECRAFT НЕ ПОДХОДИТ")
            .owner("x.x", name, module, version, "модуль не запущен: другая версия Minecraft")
            .blank()
            .tree(ConsoleTree.builder("Версии")
                .child("Нужно: " + gate.getDeclared())
                .child("На сервере: " + gate.getServer())
                .build())
            .blank()
            .tree(way.build())
            .support(PnLibraryIdentity.SUPPORT_URL)
            .blank()
            .status("Плагин остановлен, сервер продолжает запуск")
            .build();
    }
}
