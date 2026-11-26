package org.pnfolder.pnlibrary.api.remote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Decision returned by a {@link RemotePolicy}.
 *
 * <p>Besides the allow/deny decision a policy can shape the console card the administrator sees: a status shown
 * instead of the default one, a headline under the mascot, labelled details, steps to follow and links. Everything but
 * the decision is optional; {@link #allow()} and {@link #deny(String)} keep working, {@link #allowing(String)} and
 * {@link #denying(String)} open the builder.
 */
public final class RemotePolicyResult {
    private final boolean allowed;
    private final RemotePolicyExplanation explanation;
    private final String status;
    private final String headline;
    private final Map<String, String> details;
    private final List<String> steps;
    private final Map<String, String> links;

    private RemotePolicyResult(boolean allowed, RemotePolicyExplanation explanation, String status, String headline,
                               Map<String, String> details, List<String> steps, Map<String, String> links) {
        this.allowed = allowed;
        this.explanation = explanation;
        this.status = status;
        this.headline = headline;
        this.details = details;
        this.steps = steps;
        this.links = links;
    }

    /** Returns a result that lets the remote action proceed. */
    @NotNull
    public static RemotePolicyResult allow() {
        return new RemotePolicyResult(true, RemotePolicyExplanation.builder("Проверка пройдена").build(), null, null,
            Collections.<String, String>emptyMap(), Collections.<String>emptyList(),
            Collections.<String, String>emptyMap());
    }

    /** Returns a result that blocks the action with a plain message. */
    @NotNull
    public static RemotePolicyResult deny(@NotNull String message) {
        return deny(RemotePolicyExplanation.builder(orDefault(message, "Запуск запрещён")).build());
    }

    /** Returns a result that blocks the action with a structured explanation. */
    @NotNull
    public static RemotePolicyResult deny(@NotNull RemotePolicyExplanation explanation) {
        return new RemotePolicyResult(false, explanation, null, null, Collections.<String, String>emptyMap(),
            Collections.<String>emptyList(), Collections.<String, String>emptyMap());
    }

    /** Starts an allowed decision whose card can be customised. */
    @NotNull
    public static Builder allowing(@NotNull String message) {
        return new Builder(true, orDefault(message, "Проверка пройдена"));
    }

    /** Starts a denied decision whose card can be customised. */
    @NotNull
    public static Builder denying(@NotNull String message) {
        return new Builder(false, orDefault(message, "Запуск запрещён"));
    }

    private static String orDefault(String message, String fallback) {
        String trimmed = message.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    /** Whether startup or the guarded operation may continue. */
    public boolean getAllowed() {
        return allowed;
    }

    /** Structured explanation suitable for user-facing rendering. */
    @NotNull
    public RemotePolicyExplanation getExplanation() {
        return explanation;
    }

    /** Short state shown next to the installed version, or {@code null} for the default. */
    @Nullable
    public String getStatus() {
        return status;
    }

    /** Line under the mascot, or {@code null} for the default. */
    @Nullable
    public String getHeadline() {
        return headline;
    }

    /** Labelled facts such as the minimum supported version, in insertion order. */
    @NotNull
    public Map<String, String> getDetails() {
        return details;
    }

    /** What the administrator should do, in order. */
    @NotNull
    public List<String> getSteps() {
        return steps;
    }

    /** Labelled addresses such as a download page, in insertion order. */
    @NotNull
    public Map<String, String> getLinks() {
        return links;
    }

    /** Short compatibility view; structured consumers should use {@link #getExplanation()}. */
    @NotNull
    public String getMessage() {
        return explanation.getText();
    }

    /** Fluent builder for a decision with a custom card. */
    public static final class Builder {
        private final boolean allowed;
        private final RemotePolicyExplanation.Builder reasons;
        private String status;
        private String headline;
        private final Map<String, String> details = new LinkedHashMap<String, String>();
        private final List<String> steps = new ArrayList<String>();
        private final Map<String, String> links = new LinkedHashMap<String, String>();

        private Builder(boolean allowed, String text) {
            this.allowed = allowed;
            this.reasons = RemotePolicyExplanation.builder(text);
        }

        /** Sets the short status word shown to the administrator. */
        @NotNull
        public Builder status(@NotNull String value) {
            this.status = blankToNull(value);
            return this;
        }

        /** Sets the one-line summary of the decision. */
        @NotNull
        public Builder headline(@NotNull String value) {
            this.headline = blankToNull(value);
            return this;
        }

        /** Adds a reason without nested details. */
        @NotNull
        public Builder reason(@NotNull String text) {
            reasons.child(text);
            return this;
        }

        /** Adds a reason with nested details configured by {@code configure}. */
        @NotNull
        public Builder reason(@NotNull String text, @NotNull Consumer<RemotePolicyExplanation.Builder> configure) {
            reasons.branch(text, configure);
            return this;
        }

        /** Adds a labelled detail line. */
        @NotNull
        public Builder detail(@NotNull String label, @NotNull Object value) {
            if (label.trim().isEmpty()) {
                throw new IllegalArgumentException("Detail label must not be blank");
            }
            details.put(label.trim(), value.toString());
            return this;
        }

        /** Adds a step the administrator can take to resolve the denial. */
        @NotNull
        public Builder step(@NotNull String text) {
            if (text.trim().isEmpty()) {
                throw new IllegalArgumentException("Step must not be blank");
            }
            steps.add(text.trim());
            return this;
        }

        /** Adds a labelled link with more information. */
        @NotNull
        public Builder link(@NotNull String label, @NotNull String url) {
            if (label.trim().isEmpty() || url.trim().isEmpty()) {
                throw new IllegalArgumentException("Link label and address must not be blank");
            }
            links.put(label.trim(), url.trim());
            return this;
        }

        /** Creates the immutable result. */
        @NotNull
        public RemotePolicyResult build() {
            return new RemotePolicyResult(allowed, reasons.build(), status, headline,
                Collections.unmodifiableMap(new LinkedHashMap<String, String>(details)),
                Collections.unmodifiableList(new ArrayList<String>(steps)),
                Collections.unmodifiableMap(new LinkedHashMap<String, String>(links)));
        }

        private static String blankToNull(String value) {
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }
}
