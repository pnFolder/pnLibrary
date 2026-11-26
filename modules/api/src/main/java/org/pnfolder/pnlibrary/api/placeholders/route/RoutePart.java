package org.pnfolder.pnlibrary.api.placeholders.route;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** One part of the address. */
public final class RoutePart {
    private final RouteKind kind;
    private final String text;
    private final String name;
    private final List<String> arguments;

    /** A part whose declared name is its text and that has no arguments. */
    public RoutePart(@NotNull RouteKind kind, @NotNull String text) {
        this(kind, text, text);
    }

    /** A part without arguments. */
    public RoutePart(@NotNull RouteKind kind, @NotNull String text, @NotNull String name) {
        this(kind, text, name, Collections.emptyList());
    }

    /**
     * Creates a part.
     *
     * @param kind what the part is
     * @param text the text as it was written ({@code top}, {@code 3}, {@code pad0})
     * @param name the declared name: the word of a branch or field, the argument name of an argument, the name of a format
     * @param arguments what follows a colon in a format ({@code 4} for {@code pad0:4}), otherwise empty
     */
    public RoutePart(@NotNull RouteKind kind, @NotNull String text, @NotNull String name, @NotNull List<String> arguments) {
        this.kind = Objects.requireNonNull(kind);
        this.text = Objects.requireNonNull(text);
        this.name = Objects.requireNonNull(name);
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
    }

    /** What the part is. */
    @NotNull
    public RouteKind getKind() {
        return kind;
    }

    /** The text as it was written. */
    @NotNull
    public String getText() {
        return text;
    }

    /** The declared name. */
    @NotNull
    public String getName() {
        return name;
    }

    /** What follows a colon in a format, otherwise empty. */
    @NotNull
    public List<String> getArguments() {
        return arguments;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RoutePart)) {
            return false;
        }
        RoutePart that = (RoutePart) other;
        return kind == that.kind && text.equals(that.text) && name.equals(that.name) && arguments.equals(that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, text, name, arguments);
    }

    @NotNull
    @Override
    public String toString() {
        return "RoutePart(kind=" + kind + ", text=" + text + ", name=" + name + ", arguments=" + arguments + ")";
    }
}
