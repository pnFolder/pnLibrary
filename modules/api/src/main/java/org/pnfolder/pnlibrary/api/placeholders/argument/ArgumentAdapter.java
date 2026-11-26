package org.pnfolder.pnlibrary.api.placeholders.argument;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * How text from an address becomes a value of {@code T}.
 *
 * @param <T> the type of the value
 */
public final class ArgumentAdapter<T> implements ArgumentConverter<T> {
    private final String label;
    private final List<String> choices;
    private final Function<ViewerInfo, T> me;
    private final Function<String, T> parse;

    /**
     * Creates an adapter without a list of words and without {@code me}.
     *
     * @param label short word for messages, such as {@code int} or {@code player}
     * @param parse the conversion; returns {@code null} when the text does not fit
     */
    public ArgumentAdapter(@NotNull String label, @NotNull Function<String, T> parse) {
        this(label, Collections.<String>emptyList(), null, parse);
    }

    /** Creates an adapter with a fixed list of allowed words. */
    public ArgumentAdapter(@NotNull String label, @NotNull List<String> choices, @NotNull Function<String, T> parse) {
        this(label, choices, null, parse);
    }

    /**
     * Creates an adapter.
     *
     * @param label short word for messages, such as {@code int} or {@code player}
     * @param choices allowed words when the type has a fixed list
     * @param me what {@code me} means for the viewer, or {@code null} when it means nothing
     * @param parse the conversion; returns {@code null} when the text does not fit
     */
    public ArgumentAdapter(@NotNull String label, @NotNull List<String> choices, @Nullable Function<ViewerInfo, T> me,
                           @NotNull Function<String, T> parse) {
        this.label = label;
        this.choices = Collections.unmodifiableList(new ArrayList<>(choices));
        this.me = me;
        this.parse = parse;
    }

    @NotNull
    @Override
    public String getLabel() {
        return label;
    }

    /** Allowed words when the type has a fixed list. */
    @NotNull
    public List<String> getChoices() {
        return choices;
    }

    /** What {@code me} means for the viewer, or {@code null} when it means nothing. */
    @Nullable
    public Function<ViewerInfo, T> getMe() {
        return me;
    }

    /** The conversion; returns {@code null} when the text does not fit. */
    @NotNull
    public Function<String, T> getParse() {
        return parse;
    }

    @Nullable
    @Override
    public T read(@NotNull ArgumentInput input) {
        return parse.apply(input.getText());
    }

    @NotNull
    @Override
    public List<String> suggest(@NotNull ArgumentInput input) {
        String typed = input.getText().toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String choice : choices) {
            if (choice.toLowerCase(Locale.ROOT).startsWith(typed)) {
                result.add(choice);
            }
        }
        return result;
    }

    @Nullable
    @Override
    public T meOf(@NotNull ViewerInfo viewer) {
        return me == null ? null : me.apply(viewer);
    }
}
