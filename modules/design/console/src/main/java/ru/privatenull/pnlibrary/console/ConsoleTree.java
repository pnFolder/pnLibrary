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

    /** Returns this node's text.
     * @return the text displayed at this node */
    public String text() { return text; }

    /** Returns this node's children.
     * @return the immutable ordered children of this node */
    public List<ConsoleTree> children() { return children; }

    /**
     * Creates a tree builder whose root contains {@code text}.
     *
     * @param text root text
     * @return a new tree builder
     */
    public static Builder builder(String text) { return new Builder(text); }

    /** Fluent builder for immutable console explanation trees. */
    public static final class Builder {
        private final String text;
        private final List<ConsoleTree> children = new ArrayList<ConsoleTree>();

        private Builder(String text) {
            if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("tree text is required");
            this.text = text.trim();
        }

        /**
         * Adds a leaf containing {@code text}.
         *
         * @param text leaf text
         * @return this builder
         */
        public Builder child(String text) {
            children.add(builder(text).build());
            return this;
        }

        /**
         * Adds a branch and configures its children.
         *
         * @param text branch text
         * @param configure optional branch configuration
         * @return this builder
         */
        public Builder branch(String text, Consumer<Builder> configure) {
            Builder child = builder(text);
            if (configure != null) configure.accept(child);
            children.add(child.build());
            return this;
        }

        /**
         * Adds an existing child tree.
         *
         * @param child immutable child tree
         * @return this builder
         */
        public Builder child(ConsoleTree child) {
            if (child == null) throw new IllegalArgumentException("child is required");
            children.add(child);
            return this;
        }

        /** Builds the configured tree.
         * @return the immutable tree represented by this builder */
        public ConsoleTree build() { return new ConsoleTree(this); }
    }
}
