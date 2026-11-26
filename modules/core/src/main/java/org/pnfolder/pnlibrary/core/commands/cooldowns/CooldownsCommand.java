package org.pnfolder.pnlibrary.core.commands.cooldowns;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.audience.Audiences;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistry;

/**
 * {@code /pn cooldowns <player>} shows what holds an online player back, in every plugin and module;
 * {@code /pn cooldowns <player> clear} lets the player act again. Requires {@code pnlibrary.cooldowns}; the console
 * always may.
 */
public final class CooldownsCommand {
    private static final String PERMISSION = "pnlibrary.cooldowns";

    private CooldownsCommand() {
    }

    /** The command; {@code plugins} and {@code audiences} are asked again on every run, so they may change. */
    @NotNull
    public static CommandDefinition create(@NotNull final Supplier<PluginRegistry> plugins, @NotNull final Supplier<Audiences> audiences) {
        return CommandDefinition.command("cooldowns", root -> {
            root.permission(PERMISSION);
            root.noPermissionMessage("Кулдауны доступны администраторам. Нужно право " + PERMISSION + ".");
            root.consoleBypassesPermission();
            root.executes(context -> context.getSender()
                    .sendMessage(Component.text("Кулдауны игрока: /pn cooldowns <игрок> [clear]", ChatPalette.FAINT)));
            root.argument("player", ArgumentType.string(), player -> {
                player.executes(context -> show(plugins.get(), audiences.get(), context, context.<String>get("player")));
                player.literal("clear", clear -> clear.executes(
                        context -> clear(plugins.get(), audiences.get(), context, context.<String>get("player"))));
            });
        });
    }

    /** The cooldowns running for {@code subject} in every module of every plugin, the longest first. */
    @NotNull
    public static List<RunningCooldown> runningCooldowns(@NotNull PluginRegistry plugins, @NotNull UUID subject) {
        List<RunningCooldown> running = new ArrayList<RunningCooldown>();
        for (PluginRegistration plugin : plugins.all()) {
            for (ModuleContext module : plugin.all()) {
                for (Map.Entry<String, Duration> entry : module.getCooldowns().active(subject).entrySet()) {
                    running.add(new RunningCooldown(plugin.getId().getValue() + "/" + module.getId().getValue(),
                            entry.getKey(), entry.getValue()));
                }
            }
        }
        Collections.sort(running, (left, right) -> right.getRemaining().compareTo(left.getRemaining()));
        return running;
    }

    private static void show(PluginRegistry plugins, Audiences audiences, CommandContext context, String name) {
        OnlinePlayer player = audiences.player(name);
        if (player == null) {
            unknown(context, name);
            return;
        }
        List<RunningCooldown> running = runningCooldowns(plugins, player.getUniqueId());
        context.getSender().sendMessage(Component.text("Кулдауны " + name + ": " + running.size(), ChatPalette.ACCENT));
        for (RunningCooldown cooldown : running) {
            context.getSender().sendMessage(Component.text("  " + cooldown.getAction(), ChatPalette.TEXT).append(
                    Component.text("  " + cooldown.getOwner() + "  " + seconds(cooldown.getRemaining()) + " с", ChatPalette.SOFT)));
        }
    }

    private static void clear(PluginRegistry plugins, Audiences audiences, CommandContext context, String name) {
        OnlinePlayer player = audiences.player(name);
        if (player == null) {
            unknown(context, name);
            return;
        }
        int removed = 0;
        for (PluginRegistration plugin : plugins.all()) {
            for (ModuleContext module : plugin.all()) {
                removed += module.getCooldowns().clear(player.getUniqueId());
            }
        }
        context.getSender().sendMessage(Component.text("Сброшено кулдаунов у " + name + ": " + removed, ChatPalette.TEXT));
    }

    private static void unknown(CommandContext context, String name) {
        context.getSender().sendMessage(Component.text("Игрока " + name + " нет онлайн.", ChatPalette.TEXT));
    }

    private static long seconds(Duration left) {
        return (left.toMillis() + 999) / 1000;
    }
}
