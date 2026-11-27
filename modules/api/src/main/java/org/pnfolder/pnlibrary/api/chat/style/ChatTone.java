package org.pnfolder.pnlibrary.api.chat.style;

import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;

/** What a short message reports, which decides its mark and color. */
public enum ChatTone {
    /** Work has started or is still running. */
    PROGRESS(ChatPalette.ACCENT),

    /** A neutral fact, such as an available update. */
    INFO(ChatPalette.INFO),

    /** The requested work is done. */
    SUCCESS(ChatPalette.OK),

    /** Nothing failed, but the reader should act or wait. */
    WARNING(ChatPalette.WARN),

    /** The requested work failed or was refused. */
    FAILURE(ChatPalette.DANGER);

    private final TextColor color;

    ChatTone(TextColor color) {
        this.color = color;
    }

    /** The color of this tone: lavender progress, aqua facts, green success, yellow warnings and red failures. */
    public @NotNull TextColor getColor() {
        return color;
    }
}
