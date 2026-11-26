package org.pnfolder.pnlibrary.core.settings;

import java.time.Instant;
import java.util.List;
import java.util.function.BiFunction;
import org.jetbrains.annotations.NotNull;

/** One way to put a list of choices in order, for example by alphabet or by UTC offset. */
public final class ChoiceOrder {
    private final String label;
    private final BiFunction<List<String>, Instant, List<String>> sort;

    /**
     * Creates an order.
     *
     * @param label the name shown on the sorting button
     * @param sort puts the values in order as they are at the given moment (an offset changes with summer time)
     */
    public ChoiceOrder(@NotNull String label, @NotNull BiFunction<List<String>, Instant, List<String>> sort) {
        this.label = label;
        this.sort = sort;
    }

    /** The name shown on the sorting button. */
    public @NotNull String getLabel() {
        return label;
    }

    /** {@code values} in this order as they are at the moment {@code at}. */
    public @NotNull List<String> sort(@NotNull List<String> values, @NotNull Instant at) {
        return sort.apply(values, at);
    }
}
