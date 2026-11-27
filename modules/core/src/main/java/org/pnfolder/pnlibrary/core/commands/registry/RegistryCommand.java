package org.pnfolder.pnlibrary.core.commands.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.registry.Registry;
import org.pnfolder.pnlibrary.api.registry.RegistryEntry;
import org.pnfolder.pnlibrary.api.registry.Visibility;
import org.pnfolder.pnlibrary.core.commands.engine.ChatPager;

/**
 * {@code /pn registry} shows what plugins registered, one way for every kind: {@code /pn registry} lists the kinds,
 * {@code /pn registry <kind> [page]} the entries with owner and visibility, {@code /pn registry find <text>} searches all
 * kinds. Requires {@code pnlibrary.registry}; the console always may.
 */
public final class RegistryCommand {
    private static final String PERMISSION = "pnlibrary.registry";
    private static final int PAGE_SIZE = 12;

    private RegistryCommand() {
    }

    @NotNull
    public static CommandDefinition create(@NotNull final List<Registry<?>> registries) {
        return CommandDefinition.command("registry", root -> {
            root.permission(PERMISSION);
            root.noPermissionMessage("Реестры доступны администраторам. Нужно право " + PERMISSION + ".");
            root.consoleBypassesPermission();
            root.executes(context -> kinds(registries, context));
            root.literal("find", find -> find.argument("text", ArgumentType.string(),
                    text -> text.executes(context -> find(registries, context, context.<String>get("text")))));
            root.argument("kind", ArgumentType.string(), kind -> {
                kind.executes(context -> show(registries, context, context.<String>get("kind"), 1));
                kind.argument("page", ArgumentType.integer(), page -> page.executes(
                        context -> show(registries, context, context.<String>get("kind"), context.<Integer>get("page"))));
            });
        });
    }

    private static void kinds(List<Registry<?>> registries, CommandContext context) {
        context.getSender().sendMessage(Component.text("Реестры:", ChatPalette.ACCENT));
        for (Registry<?> registry : registries) {
            context.getSender().sendMessage(Component.text("  " + registry.getName() + "  " + registry.entries().size(), ChatPalette.TEXT));
        }
        context.getSender().sendMessage(Component.text("Смотреть: /pn registry <имя> [страница]; искать: /pn registry find <текст>",
                ChatPalette.FAINT));
    }

    private static void show(List<Registry<?>> registries, CommandContext context, String kind, int requested) {
        Registry<?> registry = null;
        List<String> names = new ArrayList<>();
        for (Registry<?> candidate : registries) {
            names.add(candidate.getName());
            if (registry == null && candidate.getName().equalsIgnoreCase(kind)) {
                registry = candidate;
            }
        }
        if (registry == null) {
            context.getSender().sendMessage(Component.text("Нет реестра «" + kind + "». Есть: " + String.join(", ", names),
                    ChatPalette.TEXT));
            return;
        }
        List<? extends RegistryEntry<?>> entries = registry.entries();
        int pages = ChatPager.pageCount(entries.size(), PAGE_SIZE);
        int page = ChatPager.clamp(requested, pages);
        context.getSender().sendMessage(Component.text(registry.getName() + ": " + entries.size() + ", страница " + page + "/" + pages,
                ChatPalette.ACCENT));
        for (RegistryEntry<?> entry : ChatPager.slice(entries, page, PAGE_SIZE)) {
            context.getSender().sendMessage(row(entry, null));
        }
    }

    private static void find(List<Registry<?>> registries, CommandContext context, String text) {
        String needle = text.toLowerCase(Locale.ROOT);
        List<Component> found = new ArrayList<>();
        int count = 0;
        for (Registry<?> registry : registries) {
            for (RegistryEntry<?> entry : registry.entries()) {
                if (entry.getKey().toLowerCase(Locale.ROOT).contains(needle) || entry.getOwner().toLowerCase(Locale.ROOT).contains(needle)) {
                    count++;
                    if (found.size() < PAGE_SIZE * 2) {
                        found.add(row(entry, registry.getName()));
                    }
                }
            }
        }
        context.getSender().sendMessage(Component.text("Найдено: " + count, ChatPalette.ACCENT));
        for (Component line : found) {
            context.getSender().sendMessage(line);
        }
    }

    /** One entry as a line: the key, then owner, visibility when not {@code ALL}, and description. */
    @NotNull
    public static Component row(@NotNull RegistryEntry<?> entry, @Nullable String kind) {
        String scope = entry.getVisibility() == Visibility.ALL ? "" : "  [" + entry.getVisibility().name().toLowerCase(Locale.ROOT) + "]";
        String about = entry.getDescription().isEmpty() ? "" : "  " + entry.getDescription();
        String prefix = kind == null ? "" : kind + "  ";
        return Component.text(prefix + entry.getKey(), ChatPalette.TEXT)
                .append(Component.text("  " + entry.getOwner() + scope + about, ChatPalette.SOFT));
    }
}
