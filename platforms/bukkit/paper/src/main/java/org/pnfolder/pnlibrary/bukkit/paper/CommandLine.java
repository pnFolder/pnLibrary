package org.pnfolder.pnlibrary.bukkit.paper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

final class CommandLine {
    private final String label;
    private final List<String> arguments;

    CommandLine(String label, List<String> arguments) {
        this.label = label;
        this.arguments = arguments;
    }

    String getLabel() {
        return label;
    }

    List<String> getArguments() {
        return arguments;
    }

    static CommandLine of(String input, boolean keepTrailing) {
        String trimmed = stripLeadingSlash(trimStart(input));
        // keepTrailing: a trailing space produces an empty last argument, which tab completion needs.
        // Both paths collapse multiple spaces; keepTrailing re-appends the empty token manually.
        List<String> parts = new ArrayList<>(Arrays.asList(trim(trimmed).split(" +", -1)));
        if (keepTrailing && trimmed.endsWith(" ")) {
            parts.add("");
        }
        String first = parts.get(0);
        String label = first.substring(first.lastIndexOf(':') + 1);
        return new CommandLine(label, Collections.unmodifiableList(new ArrayList<>(parts.subList(1, parts.size()))));
    }

    private static boolean isWhitespace(char symbol) {
        return Character.isWhitespace(symbol) || Character.isSpaceChar(symbol);
    }

    private static String trimStart(String text) {
        int start = 0;
        while (start < text.length() && isWhitespace(text.charAt(start))) {
            start++;
        }
        return text.substring(start);
    }

    private static String trim(String text) {
        String head = trimStart(text);
        int end = head.length();
        while (end > 0 && isWhitespace(head.charAt(end - 1))) {
            end--;
        }
        return head.substring(0, end);
    }

    private static String stripLeadingSlash(String text) {
        return text.startsWith("/") ? text.substring(1) : text;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CommandLine)) {
            return false;
        }
        CommandLine that = (CommandLine) other;
        return label.equals(that.label) && arguments.equals(that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, arguments);
    }

    @Override
    public String toString() {
        return "CommandLine(label=" + label + ", arguments=" + arguments + ")";
    }
}
