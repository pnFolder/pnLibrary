package org.pnfolder.pnlibrary.api.scenario.actions.builtin;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.config.ConfigKey;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One reaction of an {@link AttemptAction} to a kind of failure.
 *
 * <p>A code matches exactly ({@code currency.insufficient-funds}), as a family ({@code currency.*}), or everything
 * ({@code *}).
 */
public final class ErrorHandler {
    private final List<String> codes;
    @ConfigKey("do")
    private final List<Action> steps;

    /** Creates it with every setting at its default. */
    public ErrorHandler() {
        this(Collections.<String>emptyList(), Collections.<Action>emptyList());
    }

    /** Creates it with the first 1 setting; the rest keep their defaults. */
    public ErrorHandler(@NotNull List<String> codes) {
        this(codes, Collections.<Action>emptyList());
    }

    /** Creates it with every setting given. */
    public ErrorHandler(@NotNull List<String> codes, @NotNull List<Action> steps) {
        this.codes = codes;
        this.steps = steps;
    }

    /** Failure codes this handler takes; empty takes every failure. */
    @NotNull
    public List<String> getCodes() {
        return codes;
    }

    /** Actions run for it, written under {@code do}. */
    @NotNull
    public List<Action> getSteps() {
        return steps;
    }

    /** Whether this handler takes a failure with {@code code}. */
    public boolean matches(@NotNull String code) {
        if (codes.isEmpty()) {
            return true;
        }
        for (String pattern : codes) {
            if (pattern.equals("*") || pattern.equals(code)
                    || (pattern.endsWith(".*") && code.startsWith(pattern.substring(0, pattern.length() - 1)))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ErrorHandler)) {
            return false;
        }
        ErrorHandler that = (ErrorHandler) other;
        return Objects.equals(codes, that.codes)
                && Objects.equals(steps, that.steps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(codes, steps);
    }

    @NotNull
    @Override
    public String toString() {
        return "ErrorHandler(codes=" + codes + ", steps=" + steps + ")";
    }
}
