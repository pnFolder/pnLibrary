package org.pnfolder.pnlibrary.bukkit.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.chat.ChatNotice;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.api.chat.ConfirmChoice;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler;
import org.pnfolder.pnlibrary.bukkit.updates.UpdateViews;
import org.pnfolder.pnlibrary.core.updates.catalog.commands.UpdateCatalogRoutes;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogView;

/**
 * Shared reply rendering and sender plumbing of the {@code /pn} handlers.
 *
 * <p>Every reply is rendered by {@link UpdateCatalogView}, so notices and confirmations look like the catalog.
 */
public final class BukkitCommandReplies {
    private final PnLibrary library;
    private final ServerScheduler scheduler;

    public BukkitCommandReplies(PnLibrary library, ServerScheduler scheduler) {
        this.library = library;
        this.scheduler = scheduler;
    }

    /** Routes for this reader: buttons only when it can click, ASCII for the console. */
    public UpdateCatalogRoutes routes(org.pnfolder.pnlibrary.api.commands.CommandSender reply) {
        return UpdateViews.forReader(BukkitControlCommand.ROUTES, reply.getSupportsInteraction(),
            AudienceExtensions.isConsole(reply));
    }

    /** Sends one notice with {@code text}. */
    public void say(org.pnfolder.pnlibrary.api.commands.CommandSender reply, ChatTone tone, String text) {
        say(reply, tone, text, null);
    }

    /** Sends one notice with {@code text} and an optional {@code detail} line. */
    public void say(org.pnfolder.pnlibrary.api.commands.CommandSender reply, ChatTone tone, String text, String detail) {
        notices(reply, Collections.singletonList(new ChatNotice(tone, text, detail)));
    }

    /** Sends several notices in one message. */
    public void notices(org.pnfolder.pnlibrary.api.commands.CommandSender reply, List<ChatNotice> notices) {
        send(reply, UpdateViews.notices(notices, routes(reply)));
    }

    public void confirm(org.pnfolder.pnlibrary.api.commands.CommandSender reply, String title, List<String> lines,
                        String label, String command) {
        List<String> all = new ArrayList<>(lines);
        all.add("Подтверждение действует 30 секунд.");
        send(reply, UpdateCatalogView.confirmation(title, all, label, command, "Выполнить: " + command,
            routes(reply)));
    }

    /** A confirmation card with several answers on one line. */
    public void confirmChoices(org.pnfolder.pnlibrary.api.commands.CommandSender reply, String title, List<String> lines,
                               List<ConfirmChoice> choices) {
        List<String> all = new ArrayList<>(lines);
        all.add("Подтверждение действует 30 секунд.");
        send(reply, UpdateCatalogView.confirmationChoices(title, all, choices, routes(reply)));
    }

    public Consumer<String> reported(org.pnfolder.pnlibrary.api.commands.CommandSender reply) {
        return text -> say(reply, ChatTone.SUCCESS, text);
    }

    public void notRegistered(org.pnfolder.pnlibrary.api.commands.CommandSender reply, String product) {
        say(reply, ChatTone.FAILURE, "Плагин " + product + " не зарегистрирован в pnLibrary.");
    }

    /** Reports the root cause; a missing release file gets an explanation instead of a bare HTTP code. */
    public void fail(org.pnfolder.pnlibrary.api.commands.CommandSender reply, String title, Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        String detail = message.toLowerCase(java.util.Locale.ROOT).contains("http 404")
            ? "Файл обновления не найден (HTTP 404): проверьте ссылку в каталоге релизов." : message;
        say(reply, ChatTone.FAILURE, title + ".", detail);
    }

    /** Returns whether the runtime is open, telling {@code reply} to wait otherwise. */
    public boolean ready(org.pnfolder.pnlibrary.api.commands.CommandSender reply) {
        if (!library.isClosed()) {
            return true;
        }
        say(reply, ChatTone.FAILURE, "pnLibrary ещё не готова. Дождитесь полного запуска сервера.");
        return false;
    }

    /** Unwraps the native sender and runs {@code action} with it and the reply channel, unless the runtime is closed. */
    public void nativeSender(CommandContext context,
                             BiConsumer<CommandSender, org.pnfolder.pnlibrary.api.commands.CommandSender> action) {
        CommandSender sender = context.getSender() instanceof BukkitCommandSender
            ? ((BukkitCommandSender) context.getSender()).getNative() : null;
        if (sender == null) {
            say(context.getSender(), ChatTone.FAILURE, "Эта команда доступна только на Bukkit.");
        } else if (ready(context.getSender())) {
            action.accept(sender, context.getSender());
        }
    }

    /** Delivers asynchronous results on the server thread while the runtime is open. */
    public void onServerThread(Runnable action) {
        if (library.isClosed()) {
            return;
        }
        scheduler.nextTick(() -> {
            if (!library.isClosed()) {
                action.run();
            }
        });
    }

    public UpdateActor updateActor(CommandSender sender) {
        if (sender instanceof Player) {
            return new UpdateActor(UpdateActor.Kind.PLAYER, ((Player) sender).getUniqueId().toString(), sender.getName());
        }
        if (sender instanceof ConsoleCommandSender) {
            return new UpdateActor(UpdateActor.Kind.CONSOLE, "console", sender.getName());
        }
        return new UpdateActor(UpdateActor.Kind.INTEGRATION, sender.getName(), sender.getName());
    }

    private static void send(org.pnfolder.pnlibrary.api.commands.CommandSender reply, List<Component> lines) {
        for (Component line : lines) {
            reply.sendMessage(line);
        }
    }
}
