package org.pnfolder.pnlibrary.api.chat;

import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** One short message of a command, rendered by {@code ChatKit.notice}. */
public final class ChatNotice {
    private final ChatTone tone;
    private final String text;
    private final String detail;

    /**
     * @param tone visual weight of the message, such as success or failure
     * @param text the main line of the message
     * @param detail an optional second line, such as the reason of a failure
     */
    public ChatNotice(@NotNull ChatTone tone, @NotNull String text, @Nullable String detail) {
        this.tone = Objects.requireNonNull(tone, "tone");
        this.text = Objects.requireNonNull(text, "text");
        this.detail = detail;
    }

    /** A message without a second line. */
    public ChatNotice(@NotNull ChatTone tone, @NotNull String text) {
        this(tone, text, null);
    }

    /** The visual weight of the message. */
    public @NotNull ChatTone getTone() {
        return tone;
    }

    /** The main line of the message. */
    public @NotNull String getText() {
        return text;
    }

    /** The optional second line, such as the reason of a failure. */
    public @Nullable String getDetail() {
        return detail;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChatNotice)) {
            return false;
        }
        ChatNotice that = (ChatNotice) other;
        return tone == that.tone && text.equals(that.text) && Objects.equals(detail, that.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tone, text, detail);
    }

    @Override
    public String toString() {
        return "ChatNotice(tone=" + tone + ", text=" + text + ", detail=" + detail + ")";
    }
}
