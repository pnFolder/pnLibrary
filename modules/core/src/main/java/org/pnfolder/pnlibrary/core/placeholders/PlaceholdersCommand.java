package org.pnfolder.pnlibrary.core.placeholders;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.registry.Visibility;

/**
 * {@code /pn placeholders [page]} lists every placeholder with its type, default format and what is shown to
 * PlaceholderAPI; {@code /pn placeholders formats} lists the formats; {@code /pn placeholders test <address> [player uuid]}
 * works one out by hand. Requires {@code pnlibrary.placeholders}; the console always may.
 */
public final class PlaceholdersCommand {
    private static final String PERMISSION = "pnlibrary.placeholders";
    private static final int PAGE_SIZE = 12;
    private static final long TEST_TIMEOUT_SECONDS = 3L;

    private PlaceholdersCommand() {
    }

    /** Builds the {@code placeholders} command over {@code engine}. */
    public static @NotNull CommandDefinition create(@NotNull final PlaceholderEngine engine) {
        return CommandDefinition.command("placeholders", root -> {
            root.permission(PERMISSION);
            root.noPermissionMessage("Плейсхолдеры доступны администраторам. Нужно право " + PERMISSION + ".");
            root.consoleBypassesPermission();
            root.executes(context -> list(engine, context, 1));
            root.argument("page", ArgumentType.integer(), page ->
                    page.executes(context -> list(engine, context, context.<Integer>get("page"))));
            root.literal("info", info -> info.argument("address", ArgumentType.string(), address ->
                    address.executes(context -> info(engine, context, context.<String>get("address")))));
            root.literal("formats", formats -> formats.executes(context -> context.getSender().sendMessage(
                    Component.text("Форматы: " + String.join(", ", engine.formatNames()), ChatPalette.TEXT))));
            root.literal("test", test -> test.argument("address", ArgumentType.string(), address -> {
                address.executes(context -> test(engine, context, context.<String>get("address"), null));
                address.argument("player", ArgumentType.string(), player ->
                        player.executes(context -> test(engine, context, context.<String>get("address"), context.<String>get("player"))));
            }));
        });
    }

    private static void list(PlaceholderEngine engine, CommandContext context, int requested) {
        List<PlaceholderInfo> all = engine.list();
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(1, Math.min(pages, requested));
        context.getSender().sendMessage(Component.text("Плейсхолдеры: " + all.size() + ", страница " + page + "/" + pages, ChatPalette.ACCENT));
        int from = Math.min(all.size(), (page - 1) * PAGE_SIZE);
        for (PlaceholderInfo info : all.subList(from, Math.min(all.size(), from + PAGE_SIZE))) {
            context.getSender().sendMessage(row(info));
        }
        context.getSender().sendMessage(Component.text(
                "Как писать: /pn placeholders info <адрес>; проверить: /pn placeholders test <адрес> [uuid]; форматы: /pn placeholders formats",
                ChatPalette.FAINT));
    }

    /** One line of the placeholder list. */
    public static @NotNull Component row(@NotNull PlaceholderInfo info) {
        String format = info.getFormat() != null ? " |" + info.getFormat() : "";
        StringBuilder outside = new StringBuilder();
        if (!info.getPublished().isEmpty()) {
            outside.append("  PlaceholderAPI: ");
            for (int i = 0; i < info.getPublished().size(); i++) {
                outside.append(i > 0 ? ", " : "").append('%').append(info.getPublished().get(i)).append('%');
            }
        }
        String scope = info.getVisibility() == Visibility.ALL ? "" : "  [" + UsageVariants.scopeOf(info) + "]";
        String about = info.getDescription().isEmpty() ? "" : "  " + info.getDescription();
        return Component.text("{" + info.getAddress() + "}", ChatPalette.TEXT)
                .append(Component.text("  " + info.getType() + format + "  " + info.getOwner() + scope + outside + about, ChatPalette.SOFT));
    }

    private static void info(PlaceholderEngine engine, CommandContext context, String written) {
        String wanted = written.trim();
        if (wanted.startsWith("{")) {
            wanted = wanted.substring(1);
        }
        if (wanted.endsWith("}")) {
            wanted = wanted.substring(0, wanted.length() - 1);
        }
        wanted = wanted.toLowerCase(java.util.Locale.ROOT);
        boolean found = false;
        for (PlaceholderInfo entry : engine.list()) {
            if (entry.getAddress().toLowerCase(java.util.Locale.ROOT).equals(wanted)) {
                found = true;
                showUsage(context, entry);
            }
        }
        if (!found) {
            context.getSender().sendMessage(Component.text("{" + wanted + "}: такого плейсхолдера нет. Список: /pn placeholders", ChatPalette.WARN));
        }
    }

    private static void showUsage(CommandContext context, PlaceholderInfo entry) {
        context.getSender().sendMessage(row(entry));
        for (UsageVariant variant : UsageVariants.of(entry)) {
            context.getSender().sendMessage(Component.text("  " + variant.getLabel() + ": ", ChatPalette.SOFT)
                    .append(Component.text(variant.getText(), ChatPalette.TEXT))
                    .append(Component.text("  " + variant.getMeaning(), ChatPalette.FAINT)));
        }
    }

    private static void test(PlaceholderEngine engine, CommandContext context, String address, String player) {
        String expression = address;
        if (expression.startsWith("{")) {
            expression = expression.substring(1);
        }
        if (expression.endsWith("}")) {
            expression = expression.substring(0, expression.length() - 1);
        }
        UUID playerId = parseId(player);
        if (playerId == null && context.getSender() instanceof OnlinePlayer) {
            playerId = ((OnlinePlayer) context.getSender()).getUniqueId();
        }
        Object result;
        try {
            result = engine.resolveAll(expression, ViewerInfo.of(playerId)).toCompletableFuture().get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            context.getSender().sendMessage(Component.text("{" + expression + "}: нет ответа за " + TEST_TIMEOUT_SECONDS + " с", ChatPalette.WARN));
            return;
        } catch (Exception error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            String reason = error.getCause() != null && error.getCause().getMessage() != null ? error.getCause().getMessage() : error.getMessage();
            context.getSender().sendMessage(Component.text("{" + expression + "}: " + reason, ChatPalette.DANGER));
            return;
        }
        String line = result == null ? "не найден или нет значения" : result.toString();
        context.getSender().sendMessage(Component.text("{" + expression + "} = ", ChatPalette.SOFT)
                .append(Component.text(line, result == null ? ChatPalette.WARN : ChatPalette.OK)));
    }

    private static UUID parseId(String text) {
        if (text == null) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException failure) {
            return null;
        }
    }
}
