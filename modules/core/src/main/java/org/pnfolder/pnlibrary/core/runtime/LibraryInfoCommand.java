package org.pnfolder.pnlibrary.core.runtime;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.chat.ChatReader;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.version.PnLibraryApi;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;

/**
 * {@code /pninfo}: one command on every platform that shows the installed pnLibrary version,
 * the platform it runs on and a few facts that help a support request.
 *
 * <p>It needs no permission: nothing in the output is secret.
 */
public final class LibraryInfoCommand {
    private LibraryInfoCommand() {
    }

    public static @NotNull CommandDefinition create(@NotNull PnLibrary library, @NotNull PlatformIdentity identity,
                                                    @NotNull Supplier<String> version) {
        return CommandDefinition.command("pninfo", command -> {
            command.consoleBypassesPermission();
            command.executes(context -> {
                ChatKit kit = new ChatKit(ChatReader.of(context.getSender()));
                String java = System.getProperty("java.version");
                List<Component> rows = Arrays.asList(
                        kit.header(PnLibraryIdentity.NAME),
                        kit.field("Версия", kit.text(version.get(), ChatPalette.TEXT)),
                        kit.field("Платформа", kit.text(identity.getImplementationName() + " (" + identity.getId() + ")", ChatPalette.TEXT)),
                        kit.field("Версия API", kit.text(String.valueOf(PnLibraryApi.VERSION), ChatPalette.TEXT)),
                        kit.field("Java", kit.text(java != null ? java : "неизвестно", ChatPalette.TEXT)),
                        kit.field("Плагинов подключено", kit.text(String.valueOf(library.getPlugins().all().size()), ChatPalette.TEXT)));
                for (Component row : rows) {
                    context.getSender().sendMessage(row);
                }
            });
        });
    }
}
