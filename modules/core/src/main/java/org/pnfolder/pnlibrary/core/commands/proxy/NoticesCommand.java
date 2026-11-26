package org.pnfolder.pnlibrary.core.commands.proxy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.commands.engine.CommandOutput;
import org.pnfolder.pnlibrary.core.link.Link;
import org.pnfolder.pnlibrary.core.updates.restart.ImportantNotice;
import org.pnfolder.pnlibrary.core.updates.restart.ImportantNotices;
import org.pnfolder.pnlibrary.core.link.report.StagedUpdate;

/**
 * {@code /pn notices}: the important announcements of the network, such as a proxy that must be restarted to apply a
 * staged update. The menu page "Важные объявления" shows the same list.
 */
public final class NoticesCommand {
    public static final String PERMISSION = "pnlibrary.updates.restart";

    private final Supplier<List<ImportantNotice>> notices;

    /** The command over {@code notices}, which is asked again each time the list is shown. */
    public NoticesCommand(@NotNull Supplier<List<ImportantNotice>> notices) {
        this.notices = Objects.requireNonNull(notices, "notices");
    }

    /** On a game server: what the proxy and the other servers report in the network map. */
    public static @NotNull Supplier<List<ImportantNotice>> fromNetworkMap(@NotNull Link link) {
        return () -> ImportantNotices.from(link.getNetwork(), Instant.now());
    }

    /** On a proxy: its own staged update and what the game servers report to it. */
    public static @NotNull Supplier<List<ImportantNotice>> fromReports(@NotNull PnLibrary library, @NotNull Link link) {
        return () -> ImportantNotices.fromReports(StagedUpdate.waiting(library), link.getRegistry().all(), Instant.now());
    }

    public @NotNull CommandDefinition definition() {
        return CommandDefinition.command("notices", root -> {
            root.permission(PERMISSION, "pnlibrary.admin");
            root.executes(this::show);
        });
    }

    private void show(CommandContext context) {
        ChatKit kit = CommandOutput.kit(context);
        List<ImportantNotice> all = notices.get();
        List<Component> lines = new ArrayList<>();
        lines.add(kit.blank());
        lines.add(kit.header("Важные объявления", kit.text(String.valueOf(all.size()), ChatPalette.LABEL)));
        if (all.isEmpty()) {
            lines.add(kit.note("Сейчас ничего важного нет.", ChatPalette.FAINT));
        }
        for (ImportantNotice notice : all) {
            lines.add(kit.text(" ✦ " + notice.line(), ChatPalette.TEXT));
        }
        CommandOutput.send(context, lines);
    }
}
