package org.pnfolder.pnlibrary.updates.policy;

import java.util.Locale;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;

/** Names, colors and descriptions of update channels. */
public final class UpdateChannelLabels {
    private UpdateChannelLabels() {
    }

    /** The channel as it is typed in commands and shown in tags: {@code stable}, {@code rc}, {@code beta}. */
    @NotNull
    public static String commandName(@NotNull UpdateChannel channel) {
        return channel.name().toLowerCase(Locale.ROOT);
    }

    /** Channels are colored by risk, from green for stable to red for development builds. */
    @NotNull
    public static TextColor color(@NotNull UpdateChannel channel) {
        switch (channel) {
            case STABLE:
                return ChatPalette.OK;
            case RC:
                return ChatPalette.INFO;
            case BETA:
                return ChatPalette.WARN;
            case ALPHA:
                return ChatPalette.ORANGE;
            default:
                return ChatPalette.DANGER;
        }
    }

    /** What a server on this channel receives. */
    @NotNull
    public static String hint(@NotNull UpdateChannel channel) {
        switch (channel) {
            case STABLE:
                return "Только стабильные релизы";
            case RC:
                return "Кандидаты в релиз и стабильные";
            case BETA:
                return "Бета-версии и стабильные";
            case ALPHA:
                return "Экспериментальные alpha-версии: возможны ошибки";
            default:
                return "Все сборки, включая разрабатываемые: только для тестового сервера";
        }
    }

    /** The channel in a sentence, such as {@code тестовый канал Beta}. */
    @NotNull
    public static String title(@NotNull UpdateChannel channel) {
        switch (channel) {
            case STABLE:
                return "стабильный канал";
            case RC:
                return "канал RC";
            case BETA:
                return "тестовый канал Beta";
            case ALPHA:
                return "экспериментальный канал Alpha";
            default:
                return "разрабатываемый канал Dev";
        }
    }
}
