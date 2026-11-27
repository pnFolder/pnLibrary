package org.pnfolder.pnlibrary.core.journal.view;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.common.text.RussianText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Stack traces in chat: colored like source code, with frames of the JDK and the server folded. */
public final class JournalTrace {
    /** Stack trace lines in a player's chat card; the console prints all of them. */
    public static final int CARD_TRACE_LINES = 14;

    /** Stack trace frame: optional module prefix, class, method and the {@code File.java:44} location. */
    private static final Pattern FRAME = Pattern.compile("^at\\s+(?:\\S*/)?([\\w$.]+)\\.([\\w$<>]+)\\((.*)\\)$");

    /** Exception line: optional {@code Caused by: }, the class and its message. */
    private static final Pattern HEAD = Pattern.compile("^(Caused by: |Suppressed: )?([\\w$.]+)(?::\\s?(.*))?$");

    /** Packages of the JDK, the server and common libraries: their frames are dimmed, the plugin's own stay bright. */
    private static final List<String> FRAMEWORK = Arrays.asList(
        "java.", "javax.", "jdk.", "sun.", "org.bukkit.", "io.papermc.", "net.minecraft.", "com.mojang.",
        "org.spigotmc.", "net.md_5.", "com.destroystokyo.", "io.netty.", "org.apache.", "kotlin.", "kotlinx.",
        "com.velocitypowered.", "net.kyori.", "org.jetbrains.");

    private JournalTrace() {
    }

    /** Colors one trace line and tells whether it is a framework frame. */
    public static @NotNull TraceLine traceLine(@NotNull ChatKit kit, @NotNull String line) {
        String text = line.replace("\t", " ").trim();
        Matcher frame = FRAME.matcher(text);
        if (frame.matches()) {
            String className = frame.group(1);
            boolean framework = isFramework(className);
            return new TraceLine(frameLine(kit, className, frame.group(2), frame.group(3), framework), framework);
        }
        Matcher head = HEAD.matcher(text);
        if (head.matches()) {
            return new TraceLine(headLine(kit, head.group(1), head.group(2), head.group(3)), false);
        }
        return new TraceLine(kit.text("    " + text, ChatPalette.FAINT), false);
    }

    private static boolean isFramework(String className) {
        for (String prefix : FRAMEWORK) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static Component headLine(ChatKit kit, String prefix, String className, String message) {
        Component head = kit.text("  ");
        if (prefix != null && !prefix.isEmpty()) {
            head = head.append(kit.text(prefix, ChatPalette.ORANGE));
        }
        int dot = className.lastIndexOf('.');
        String packageName = dot < 0 ? "" : className.substring(0, dot + 1);
        head = head.append(kit.text(packageName, ChatPalette.FAINT))
            .append(kit.text(className.substring(dot + 1), ChatPalette.DANGER));
        if (message != null && !message.isEmpty()) {
            head = head.append(kit.text(": ", ChatPalette.SOFT)).append(kit.text(message, ChatPalette.TEXT));
        }
        return head;
    }

    /** A stack frame: the plugin's own frames are bright and the frames of the JDK and the server are dimmed. */
    private static Component frameLine(ChatKit kit, String className, String method, String location,
                                       boolean framework) {
        String simple = className.substring(className.lastIndexOf('.') + 1);
        Component component;
        if (framework) {
            component = kit.text("    · ", ChatPalette.FAINT)
                .append(kit.text(simple + "." + method, ChatPalette.FAINT))
                .append(kit.text("  " + location, ChatPalette.FAINT));
        } else {
            component = kit.text("    ▸ ", ChatPalette.ORANGE)
                .append(kit.text(simple, ChatPalette.TEXT))
                .append(kit.text(".", ChatPalette.FAINT))
                .append(kit.text(method, ChatPalette.ACCENT))
                .append(kit.text("  " + location, ChatPalette.SOFT));
        }
        return component.hoverEvent(kit.tooltip(
            kit.text(className + "." + method, framework ? ChatPalette.FAINT : ChatPalette.TEXT),
            kit.text(location, framework ? ChatPalette.FAINT : ChatPalette.SOFT)));
    }

    /**
     * The whole stack trace colored like source code: exception classes in red with their package dimmed, the plugin's
     * own frames in amber and green, frames of the JDK and the server dimmed. With {@code full} false, as in chat,
     * the dimmed frames of one run are folded into a single line and the list is cut; the console shows every line.
     */
    public static @NotNull TraceView traceView(@NotNull ChatKit kit, @NotNull List<String> trace, boolean full) {
        List<TraceLine> rendered = new ArrayList<>();
        for (String line : trace) {
            rendered.add(traceLine(kit, line));
        }
        if (full) {
            List<Component> all = new ArrayList<>();
            for (TraceLine line : rendered) {
                all.add(line.getComponent());
            }
            return new TraceView(all, false);
        }
        return fold(kit, rendered);
    }

    private static TraceView fold(ChatKit kit, List<TraceLine> rendered) {
        List<Component> out = new ArrayList<>();
        int folded = 0;
        int cut = 0;
        boolean anyFramework = false;
        for (TraceLine line : rendered) {
            if (line.isFramework()) {
                folded++;
                anyFramework = true;
                continue;
            }
            folded = flush(kit, out, folded);
            if (out.size() >= CARD_TRACE_LINES) {
                cut++;
            } else {
                out.add(line.getComponent());
            }
        }
        flush(kit, out, folded);
        if (cut > 0) {
            out.add(kit.note("    … ещё " + RussianText.counted(cut, "строка", "строки", "строк")
                + ", их даёт кнопка [скопировать]", ChatPalette.FAINT));
        }
        return new TraceView(out, cut > 0 || anyFramework);
    }

    private static int flush(ChatKit kit, List<Component> out, int folded) {
        if (folded > 0) {
            out.add(kit.text("    · … " + folded + " " + RussianText.plural(folded, "служебный вызов",
                "служебных вызова", "служебных вызовов"), ChatPalette.FAINT));
        }
        return 0;
    }
}
