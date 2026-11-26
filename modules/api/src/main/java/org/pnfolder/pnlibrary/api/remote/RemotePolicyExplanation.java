package org.pnfolder.pnlibrary.api.remote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

/** Structured, arbitrarily nested explanation returned by a remote policy. */
public final class RemotePolicyExplanation {
    private final String text;
    private final List<RemotePolicyExplanation> children;

    private RemotePolicyExplanation(Builder builder) {
        this.text = builder.text;
        this.children = Collections.unmodifiableList(new ArrayList<RemotePolicyExplanation>(builder.children));
    }

    /** Starts an explanation whose root contains non-blank {@code text}. */
    @NotNull
    public static Builder builder(@NotNull String text) {
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Remote policy explanation must not be blank");
        }
        return new Builder(normalized);
    }

    /** Text displayed for this explanation node. */
    @NotNull
    public String getText() {
        return text;
    }

    /** Immutable child explanations providing additional detail. */
    @NotNull
    public List<RemotePolicyExplanation> getChildren() {
        return children;
    }

    /** Fluent builder for a structured {@link RemotePolicyExplanation}. */
    public static final class Builder {
        private final String text;
        private final List<RemotePolicyExplanation> children = new ArrayList<RemotePolicyExplanation>();

        private Builder(String text) {
            this.text = text;
        }

        /** Adds a nested line with plain text. */
        @NotNull
        public Builder child(@NotNull String text) {
            children.add(builder(text).build());
            return this;
        }

        /** Adds a nested line whose own children are configured by {@code configure}. */
        @NotNull
        public Builder branch(@NotNull String text, @NotNull Consumer<Builder> configure) {
            Builder nested = builder(text);
            configure.accept(nested);
            children.add(nested.build());
            return this;
        }

        /** Adds an already built explanation as a nested line. */
        @NotNull
        public Builder child(@NotNull RemotePolicyExplanation value) {
            children.add(value);
            return this;
        }

        /** Creates the immutable explanation. */
        @NotNull
        public RemotePolicyExplanation build() {
            return new RemotePolicyExplanation(this);
        }
    }
}
