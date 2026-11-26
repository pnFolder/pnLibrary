package org.pnfolder.pnlibrary.core.menu.style;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** One labelled line of an item's description; an empty label continues the line above. */
public final class Fact {
    private final String label;
    private final String value;

    public Fact(@NotNull String label, @NotNull String value) {
        this.label = Objects.requireNonNull(label, "label");
        this.value = Objects.requireNonNull(value, "value");
    }

    public static @NotNull Fact of(@NotNull String label, @NotNull String value) {
        return new Fact(label, value);
    }

    /** Facts from alternating labels and values: {@code list("Версия", "1.0", "Итог", "ok")}. */
    public static @NotNull List<Fact> list(@NotNull String... labelsAndValues) {
        List<Fact> facts = new ArrayList<>();
        for (int index = 0; index + 1 < labelsAndValues.length; index += 2) {
            facts.add(new Fact(labelsAndValues[index], labelsAndValues[index + 1]));
        }
        return facts;
    }

    public @NotNull String getLabel() {
        return label;
    }

    public @NotNull String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Fact && label.equals(((Fact) other).label) && value.equals(((Fact) other).value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, value);
    }

    @Override
    public String toString() {
        return label + "=" + value;
    }
}
