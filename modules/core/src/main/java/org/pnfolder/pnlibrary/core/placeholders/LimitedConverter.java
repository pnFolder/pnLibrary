package org.pnfolder.pnlibrary.core.placeholders;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentInput;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;

/** A converter built from the options of {@code @Argument}: it reads with {@code reader} and keeps the {@code me} of the inner one. */
public final class LimitedConverter implements ArgumentConverter<Object> {
    private final ArgumentConverter<Object> inner;
    private final String label;
    private final List<String> words;
    private final Function<ArgumentInput, Object> reader;

    public LimitedConverter(ArgumentConverter<Object> inner, String label, List<String> words, Function<ArgumentInput, Object> reader) {
        this.inner = inner;
        this.label = label;
        this.words = words;
        this.reader = reader;
    }

    @Override
    public @NotNull String getLabel() {
        return label;
    }

    @Override
    public @Nullable Object read(@NotNull ArgumentInput input) {
        return reader.apply(input);
    }

    @Override
    public @NotNull List<String> suggest(@NotNull ArgumentInput input) {
        if (words.isEmpty()) {
            return inner.suggest(input);
        }
        List<String> matching = new ArrayList<>();
        for (String word : words) {
            if (word.regionMatches(true, 0, input.getText(), 0, input.getText().length())) {
                matching.add(word);
            }
        }
        return matching;
    }

    @Override
    public @Nullable Object meOf(@NotNull ViewerInfo viewer) {
        return inner.meOf(viewer);
    }
}
