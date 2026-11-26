package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds readable validation rules without manually creating problem lists.
 *
 * <pre>{@code
 * ConfigValueValidator<Settings> validator = new ConfigValidatorBuilder<>()
 *         .require("database.port", "must be between 1 and 65535", s -> s.database.port >= 1 && s.database.port <= 65535)
 *         .build();
 * }</pre>
 */
public final class ConfigValidatorBuilder<T> {
    private final List<Rule<T>> rules = new ArrayList<>();

    /** Adds a rule for the YAML {@code path}. */
    @NotNull
    public ConfigValidatorBuilder<T> require(@NotNull String path, @NotNull String message,
                                             @NotNull ConfigCondition<T> condition) {
        if (ConfigText.isBlank(path)) {
            throw new IllegalArgumentException("Configuration path cannot be blank");
        }
        rules.add(new Rule<>(path, message, condition));
        return this;
    }

    /** Creates an immutable validator from the configured rules. */
    @NotNull
    public ConfigValueValidator<T> build() {
        final List<Rule<T>> snapshot = new ArrayList<>(rules);
        return value -> {
            List<ConfigProblem> problems = new ArrayList<>();
            for (Rule<T> rule : snapshot) {
                if (!rule.condition.test(value)) {
                    problems.add(new ConfigProblem(rule.path, rule.message));
                }
            }
            return problems;
        };
    }

    private static final class Rule<T> {
        final String path;
        final String message;
        final ConfigCondition<T> condition;

        Rule(String path, String message, ConfigCondition<T> condition) {
            this.path = path;
            this.message = message;
            this.condition = condition;
        }
    }
}
