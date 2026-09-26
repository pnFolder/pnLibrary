package ru.privatenull.pnlibrary.console;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** Immutable, arbitrarily nested explanation tree rendered by {@link ConsoleCard}. */
public final class ConsoleTree {
    private final String text;
    private final List<ConsoleTree> children;

    private ConsoleTree(Builder builder) {
        this.text = builder.text;
        this.children = Collections.unmodifiableList(new ArrayList<ConsoleTree>(builder.children));
    }

    public String text() { return text; }
    public List<ConsoleTree> children() { return children; }
    public static Builder builder(String text) { return new Builder(text); }

    public static final class Builder {
        private final String text;
        private final List<ConsoleTree> children = new ArrayList<ConsoleTree>();

        private Builder(String text) {
            if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("tree text is required");
            this.text = text.trim();
        }

        public Builder child(String text) {
            children.add(builder(text).build());
            return this;
        }

        public Builder branch(String text, Consumer<Builder> configure) {
            Builder child = builder(text);
            if (configure != null) configure.accept(child);
            children.add(child.build());
            return this;
        }

        public Builder child(ConsoleTree child) {
            if (child == null) throw new IllegalArgumentException("child is required");
            children.add(child);
            return this;
        }

        public ConsoleTree build() { return new ConsoleTree(this); }
    }
}
