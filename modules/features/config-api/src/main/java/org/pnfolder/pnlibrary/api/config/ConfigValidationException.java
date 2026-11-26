package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Aggregated semantic validation failure for a typed configuration. */
public class ConfigValidationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final transient List<ConfigProblem> problems;

    /** Creates a failure that reports every problem produced by the validator. */
    public ConfigValidationException(@NotNull List<ConfigProblem> problems) {
        super(describe(problems));
        this.problems = problems;
    }

    /** Every problem reported by the validator. */
    @NotNull
    public List<ConfigProblem> getProblems() {
        return problems;
    }

    private static String describe(List<ConfigProblem> problems) {
        StringBuilder text = new StringBuilder("Invalid configuration: ");
        for (int i = 0; i < problems.size(); i++) {
            if (i > 0) {
                text.append("; ");
            }
            text.append(problems.get(i).getPath()).append(": ").append(problems.get(i).getMessage());
        }
        return text.toString();
    }
}
