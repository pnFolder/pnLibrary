package org.pnfolder.pnlibrary.api.chat;

import org.pnfolder.pnlibrary.api.chat.page.ChatAscii;
import org.pnfolder.pnlibrary.api.chat.page.CommandHint;
import org.pnfolder.pnlibrary.api.chat.page.CommandHintLines;
import org.pnfolder.pnlibrary.api.chat.page.Confirmations;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import java.util.Objects;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.chat.ChatNotice;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.api.chat.ConfirmChoice;
import org.pnfolder.pnlibrary.common.lang.Failures;

/**
 * Building blocks of every pnLibrary chat page, so all of them share one look.
 *
 * <p>A page starts with an empty line, then a bold accent title with details beside it, optional sky-blue hints,
 * blank-line separated sections of rows and a footer of bracketed buttons whose brackets are gray and whose label
 * carries the color. Details live in multi-line hover cards. A reader that cannot click gets the same rows without
 * buttons; the console additionally gets ASCII only. Stored text is only ever rendered as plain text, never
 * interpreted as markup.
 */
public class ChatKit {
    /** The longest stored text a page shows, such as an error message. */
    public static final int MAX_MESSAGE = 512;

    /** The longest single line a page shows, such as a link in a hover card. */
    public static final int MAX_LINE = 120;

    private static final int MAX_LINK = 4096;

    private final ChatReader reader;

    /** @param reader the reader whose capabilities decide buttons, hover cards and characters */
    public ChatKit(@NotNull ChatReader reader) {
        this.reader = Objects.requireNonNull(reader, "reader");
    }

    /** The reader whose capabilities decide buttons, hover cards and characters. */
    public @NotNull ChatReader getReader() {
        return reader;
    }

    /** A page: an empty line first, so it never runs into the chat above it, such as the previous page. */
    public @NotNull List<Component> framed(@NotNull List<Component> lines) {
        List<Component> page = new ArrayList<>(lines.size() + 1);
        page.add(Component.empty());
        page.addAll(lines);
        return page;
    }

    /** The first line of a page: the title in bold accent. */
    public @NotNull Component header(@NotNull String title) {
        return header(title, null);
    }

    /** The first line of a page: the title in bold accent, then its details after two spaces. */
    public @NotNull Component header(@NotNull String title, @Nullable Component details) {
        Component name = Component.text(shown(title), ChatPalette.ACCENT).decoration(TextDecoration.BOLD, true);
        return details == null ? name : Component.empty().append(name).append(text("  ")).append(details);
    }

    /** Sections are separated by an empty line, never by rules or frames. */
    public @NotNull Component blank() {
        return Component.empty();
    }

    /** An indented {@code Label: value} row, the label in sky blue. */
    public @NotNull Component field(@NotNull String label, @NotNull Component value) {
        return text("  " + label + ": ", ChatPalette.LABEL).append(value);
    }

    /** A {@code Label: value} line of a hover card. */
    public @NotNull Component hoverField(@NotNull String label, @NotNull Component value) {
        return text(label + ": ", ChatPalette.LABEL).append(value);
    }

    /** A row the reader acts on, such as a settings choice: the label in white, so it reads as a control. */
    public @NotNull Component setting(@NotNull String label, @NotNull Component value) {
        return text("  " + label + ": ", ChatPalette.TEXT).append(value);
    }

    /** A hint or description under the title. */
    public @NotNull Component note(@NotNull String value) {
        return note(value, ChatPalette.LABEL);
    }

    /** A hint or description under the title, in {@code color}. */
    public @NotNull Component note(@NotNull String value, @NotNull TextColor color) {
        return text(" " + value, color);
    }

    /** A separator between parts of a line. */
    public @NotNull Component dot() {
        return text(" · ", ChatPalette.SOFT);
    }

    /** Lines of a hover card joined with line breaks. */
    public @NotNull HoverEvent<Component> tooltip(@NotNull Component... lines) {
        Component card = lines[0];
        for (int index = 1; index < lines.length; index++) {
            card = card.append(Component.newline()).append(lines[index]);
        }
        return HoverEvent.showText(card);
    }

    /** {@code [label]} with gray brackets and the label in {@code color}. */
    public @NotNull Component bracketed(@NotNull String label, @NotNull TextColor color) {
        return text("[", ChatPalette.SOFT).append(text(label, color)).append(text("]", ChatPalette.SOFT));
    }

    /**
     * A clickable {@code [label]} that runs {@code command}, or nothing when the reader cannot click; pages list the
     * commands for such readers instead.
     */
    public @NotNull Component button(@NotNull String label, @NotNull String command, @NotNull String hint,
                                     @NotNull TextColor color) {
        String bare = label.startsWith("[") ? label.substring(1) : label;
        if (bare.endsWith("]")) {
            bare = bare.substring(0, bare.length() - 1);
        }
        return runButton(bare, command, hint, color);
    }

    /** A clickable {@code [label]} that opens {@code link}, or nothing when the reader cannot click or the link is not safe. */
    public @NotNull Component linkButton(@NotNull String label, @NotNull String link, @NotNull String hint,
                                         @NotNull TextColor color) {
        if (!reader.isInteractive() || !safeLink(link)) {
            return Component.empty();
        }
        return bracketed(label, color).clickEvent(ClickEvent.openUrl(link))
            .hoverEvent(tooltip(text(hint, ChatPalette.TEXT), text(take(link, MAX_LINE), ChatPalette.LABEL)));
    }

    /** A bracketed choice that runs {@code command} on click, in {@code color}. */
    public @NotNull Component option(@NotNull String label, @NotNull String command, @NotNull String hint,
                                     @NotNull TextColor color) {
        return runButton(label, command, hint, color);
    }

    private Component runButton(String label, String command, String hint, TextColor color) {
        if (!reader.isInteractive()) {
            return Component.empty();
        }
        return bracketed(label, color).clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(tooltip(text(hint, ChatPalette.TEXT), blank(), text(command, ChatPalette.LABEL)));
    }

    /** The current choice of a settings row: marked with a check and in its own color, so it reads as already pressed. */
    public @NotNull Component selected(@NotNull String label, @NotNull TextColor color) {
        return Component.text(shown("[" + glyph('✔') + " " + label + "]"), color)
            .hoverEvent(tooltip(text("Выбрано сейчас", color)));
    }

    /** {@code component} led by a space, or nothing when it is empty, such as a hidden button. */
    public @NotNull Component spaced(@NotNull Component component) {
        return component.equals(Component.empty()) ? component : text(" ").append(component);
    }

    /** Buttons separated by spaces on one indented line, or {@code null} when the reader cannot click any of them. */
    public @Nullable Component actions(@Nullable Component... buttons) {
        Component line = null;
        for (Component button : buttons) {
            if (button == null || button.equals(Component.empty())) {
                continue;
            }
            line = line == null ? text("  ").append(button) : line.append(text(" ")).append(button);
        }
        return line;
    }

    /** A section heading wrapped in guillemets, such as {@code «Сегодня»}, in the accent color. */
    public @NotNull Component section(@NotNull String title) {
        return text("  " + glyph('«') + title + glyph('»'), ChatPalette.ACCENT);
    }

    /**
     * A {@code «Команды»} section: one command per line, then what it does. Placeholders such as {@code <игрок>}
     * stand out in the accent; a reader who can click gets the command typed into the chat box by clicking its line.
     * A hint without a command is left out.
     */
    public @NotNull List<Component> commandHints(@NotNull CommandHint... hints) {
        return new CommandHintLines(this).of(hints);
    }

    /** A counter such as {@code 3 плагина} (the Russian word is sample text): the number in {@code color}, the word in sky blue. */
    public @NotNull Component chip(int count, @NotNull String word, @NotNull TextColor color) {
        return text(String.valueOf(count), color).append(text(" " + word, ChatPalette.LABEL));
    }

    /** One short message: a mark in the tone's color, the text, and its detail on the next line. */
    public @NotNull List<Component> notice(@NotNull ChatNotice notice) {
        TextColor textColor = notice.getTone() == ChatTone.FAILURE ? ChatPalette.DANGER : ChatPalette.TEXT;
        List<Component> lines = new ArrayList<>(2);
        lines.add(mark(notice.getTone()).append(text(take(notice.getText(), MAX_MESSAGE), textColor)));
        String detail = notice.getDetail();
        if (detail != null && !detail.trim().isEmpty()) {
            lines.add(text("    " + take(detail, MAX_MESSAGE), ChatPalette.LABEL));
        }
        return lines;
    }

    /** The indented mark that opens a notice line, in the tone's color; text appended to it reads as a notice. */
    public @NotNull Component mark(@NotNull ChatTone tone) {
        char symbol;
        switch (tone) {
            case PROGRESS:
                symbol = '»';
                break;
            case INFO:
                symbol = '■';
                break;
            case SUCCESS:
                symbol = '√';
                break;
            default:
                symbol = '!';
                break;
        }
        return text("  " + glyph(symbol) + " ", tone.getColor());
    }

    /**
     * A page that asks to confirm a risky action: what will happen, one sentence per line, and one button; a reader
     * that cannot click is told the command to type.
     */
    public @NotNull List<Component> confirmation(@NotNull String title, @NotNull List<String> lines,
                                                 @NotNull String label, @NotNull String command, @NotNull String hint) {
        return new Confirmations(this).single(title, lines, label, command, hint);
    }

    /**
     * Like {@link #confirmation}, but with several answers on one line; a reader that cannot click gets one line per
     * answer with the command to type.
     */
    public @NotNull List<Component> confirmationChoices(@NotNull String title, @NotNull List<String> lines,
                                                        @NotNull List<ConfirmChoice> choices) {
        return new Confirmations(this).choices(title, lines, choices);
    }

    /** Plain text; the console gets the ASCII form. */
    public @NotNull Component text(@NotNull String value) {
        return Component.text(shown(value));
    }

    /** Plain text in {@code color}; the console gets the ASCII form. */
    public @NotNull Component text(@NotNull String value, @NotNull TextColor color) {
        Component component = Component.text(shown(value));
        // Consoles show only the 16 classic colors; a custom RGB value there turns into broken escape codes.
        return component.color(reader.isAscii() ? NamedTextColor.nearestTo(color) : color);
    }

    /** {@code value} as this reader can display it. */
    public @NotNull String shown(@NotNull String value) {
        return reader.isAscii() ? ChatAscii.of(value) : value;
    }

    /** One glyph as this reader can display it. */
    public @NotNull String glyph(char symbol) {
        return shown(String.valueOf(symbol));
    }

    /** Whether {@code value} is an HTTPS link without credentials, the only kind a page may open. */
    public boolean safeLink(@NotNull String value) {
        return Failures.runCatchingNonFatal(() -> {
            URI uri = new URI(value);
            return value.length() <= MAX_LINK && "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null && uri.getUserInfo() == null;
        }).getOrDefault(false);
    }

    private static String take(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length);
    }
}
