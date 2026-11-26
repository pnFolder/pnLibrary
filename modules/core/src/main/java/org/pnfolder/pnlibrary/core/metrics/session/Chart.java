package org.pnfolder.pnlibrary.core.metrics.session;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Typed, lazy chart definitions shared by provider adapters.
 *
 * <p>Suppliers are retained without evaluation; a provider translates the chart into its native form and calls the
 * supplier only when it collects a sample.
 */
public abstract class Chart {
    private final String id;

    private Chart(String id) {
        this.id = id;
    }

    /** Stable chart identifier used by the provider. */
    @NotNull
    public final String getId() {
        return id;
    }

    @NotNull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "(id=" + id + ")";
    }

    /** A single categorical string sample; the supplier gives {@code null} when there is no category. */
    public static final class SimplePie extends Chart {
        private final Supplier<String> value;

        public SimplePie(@NotNull String id, @NotNull Supplier<String> value) {
            super(id);
            this.value = value;
        }

        /** Supplier of the current category, or {@code null} when there is none. */
        @NotNull
        public Supplier<String> getValue() {
            return value;
        }
    }

    /** Category-to-count samples. */
    public static final class AdvancedPie extends Chart {
        private final Supplier<Map<String, Integer>> values;

        public AdvancedPie(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values) {
            super(id);
            this.values = values;
        }

        /** Supplier of category-to-count pairs. */
        @NotNull
        public Supplier<Map<String, Integer>> getValues() {
            return values;
        }
    }

    /** Group-to-category-to-count samples. */
    public static final class DrilldownPie extends Chart {
        private final Supplier<Map<String, Map<String, Integer>>> values;

        public DrilldownPie(@NotNull String id, @NotNull Supplier<Map<String, Map<String, Integer>>> values) {
            super(id);
            this.values = values;
        }

        /** Supplier of group-to-category-to-count pairs. */
        @NotNull
        public Supplier<Map<String, Map<String, Integer>>> getValues() {
            return values;
        }
    }

    /** A single numeric time-series sample. */
    public static final class SingleLine extends Chart {
        private final Supplier<Integer> value;

        public SingleLine(@NotNull String id, @NotNull Supplier<Integer> value) {
            super(id);
            this.value = value;
        }

        /** Supplier of the current number. */
        @NotNull
        public Supplier<Integer> getValue() {
            return value;
        }
    }

    /** Named numeric time-series samples. */
    public static final class MultiLine extends Chart {
        private final Supplier<Map<String, Integer>> values;

        public MultiLine(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values) {
            super(id);
            this.values = values;
        }

        /** Supplier of series name-to-number pairs. */
        @NotNull
        public Supplier<Map<String, Integer>> getValues() {
            return values;
        }
    }

    /** Named numeric bars. */
    public static final class SimpleBar extends Chart {
        private final Supplier<Map<String, Integer>> values;

        public SimpleBar(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values) {
            super(id);
            this.values = values;
        }

        /** Supplier of bar name-to-number pairs. */
        @NotNull
        public Supplier<Map<String, Integer>> getValues() {
            return values;
        }
    }

    /** Named numeric arrays of bars. */
    public static final class AdvancedBar extends Chart {
        private final Supplier<Map<String, int[]>> values;

        public AdvancedBar(@NotNull String id, @NotNull Supplier<Map<String, int[]>> values) {
            super(id);
            this.values = values;
        }

        /** Supplier of bar name-to-values pairs. */
        @NotNull
        public Supplier<Map<String, int[]>> getValues() {
            return values;
        }
    }
}
