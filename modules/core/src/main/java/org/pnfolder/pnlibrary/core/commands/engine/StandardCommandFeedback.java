package org.pnfolder.pnlibrary.core.commands.engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.error.BadUsage;
import org.pnfolder.pnlibrary.api.commands.error.CommandError;
import org.pnfolder.pnlibrary.api.commands.error.CommandFailed;
import org.pnfolder.pnlibrary.api.commands.error.ConsoleOnly;
import org.pnfolder.pnlibrary.api.commands.error.InvalidArgument;
import org.pnfolder.pnlibrary.api.commands.error.NoPermission;
import org.pnfolder.pnlibrary.api.commands.error.OnCooldown;
import org.pnfolder.pnlibrary.api.commands.error.PlayerNotFound;
import org.pnfolder.pnlibrary.api.commands.error.PlayerOnly;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;
import org.pnfolder.pnlibrary.common.lang.Failures;

/** Last link of the error chain; falls back through base classes and uses the CommandFailed text as a last resort. */
public final class StandardCommandFeedback {
    public static final String DEFAULT_NO_PERMISSION = "Недостаточно прав для этой команды.";
    public static final String DEFAULT_USAGE = "Использование: {usage}";
    private static final String DEFAULT_INVALID = "Неверное значение «{value}» для {argument}. {usage}";

    private final Sound deniedSound;
    private final Map<Class<?>, String> texts = new HashMap<>();

    public StandardCommandFeedback(
            @NotNull String deniedMessage,
            @Nullable Sound deniedSound,
            @NotNull String usageFormat,
            @NotNull String invalidArgumentMessage,
            @NotNull String playerNotFoundMessage,
            @NotNull String playerOnlyMessage,
            @NotNull String consoleOnlyMessage,
            @NotNull String cooldownMessage,
            @NotNull String failedMessage) {
        this.deniedSound = deniedSound;
        texts.put(NoPermission.class, deniedMessage);
        texts.put(BadUsage.class, usageFormat);
        texts.put(InvalidArgument.class, invalidArgumentMessage);
        texts.put(PlayerNotFound.class, playerNotFoundMessage);
        texts.put(PlayerOnly.class, playerOnlyMessage);
        texts.put(ConsoleOnly.class, consoleOnlyMessage);
        texts.put(OnCooldown.class, cooldownMessage);
        texts.put(CommandFailed.class, failedMessage);
    }

    public StandardCommandFeedback(@NotNull String deniedMessage, @Nullable Sound deniedSound, @NotNull String usageFormat) {
        this(deniedMessage, deniedSound, usageFormat, DEFAULT_INVALID, "Игрок {value} сейчас не в сети.",
                "Эту команду можно использовать только в игре.", "Эту команду можно использовать только из консоли.",
                "Подождите {seconds} с перед повторным использованием.", "Не удалось выполнить команду.");
    }

    public StandardCommandFeedback(@NotNull String deniedMessage) {
        this(deniedMessage, null, DEFAULT_USAGE);
    }

    public StandardCommandFeedback() {
        this(DEFAULT_NO_PERMISSION);
    }

    /** The feedback texts and sound that the library configuration asks for. */
    @NotNull
    public static StandardCommandFeedback from(@NotNull PnLibraryConfig config) {
        return new StandardCommandFeedback(
                config.getNoPermissionMessage(), soundOf(config.getNoPermissionSound()), config.getUsageMessage(),
                config.getInvalidArgumentMessage(), config.getPlayerNotFoundMessage(), config.getPlayerOnlyMessage(),
                config.getConsoleOnlyMessage(), config.getCooldownMessage(), config.getCommandFailedMessage());
    }

    @Nullable
    private static Sound soundOf(String name) {
        if (name.trim().isEmpty()) {
            return null;
        }
        try {
            return Sound.sound(Key.key(name), Sound.Source.MASTER, 1f, 1f);
        } catch (Throwable error) {
            Failures.rethrowIfFatal(error);
            return null;
        }
    }

    @Nullable
    public Sound getDeniedSound() {
        return deniedSound;
    }

    /** Tells the sender what went wrong; {@code help} lists the ways to continue a command typed incompletely. */
    public void answer(@NotNull CommandContext context, @NotNull CommandError error, @NotNull List<String> help) {
        Class<?> type = error.getClass();
        String template = error.getDefaultMessage();
        while (template == null && type != null) {
            template = texts.get(type);
            type = type.getSuperclass();
        }
        String text = error.format(template == null ? texts.get(CommandFailed.class) : template);
        boolean warning = error instanceof NoPermission || error instanceof OnCooldown;
        ChatTone tone = warning ? ChatTone.WARNING : ChatTone.FAILURE;
        if (error instanceof BadUsage) {
            SenderNotices.usage(context.getSender(), text, help);
        } else {
            SenderNotices.notice(context.getSender(), tone, text, null);
        }
        if (error instanceof NoPermission && deniedSound != null) {
            context.getSender().playSound(deniedSound);
        }
    }

    public void answer(@NotNull CommandContext context, @NotNull CommandError error) {
        answer(context, error, java.util.Collections.emptyList());
    }
}
