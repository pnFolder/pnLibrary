package org.pnfolder.pnlibrary.api.placeholders.argument;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Turns the text of one argument into a value of {@code T}. Write one for a class that is not yours, and name it on the
 * parameter: {@code @Argument(converter = ClanByName.class) ArgumentValue<Clan> clan}. The class needs a constructor
 * without parameters (or is a Kotlin {@code object}). A converter registered with {@link ArgumentAdapters#register} is
 * the default for its type.
 *
 * @param <T> the type of the value
 */
public interface ArgumentConverter<T> {
    /** Short words for messages and lists: {@code clan}, {@code number 1..9}. */
    @NotNull
    String getLabel();

    /** The value for {@link ArgumentInput#getText()}, or {@code null} when it does not fit. {@link ArgumentInput#fail} tells why. */
    @Nullable
    T read(@NotNull ArgumentInput input);

    /**
     * The same, for work that takes time (a database). The default calls {@link #read}; override this one instead of
     * {@link #read} only when the answer really comes later.
     */
    @NotNull
    default CompletionStage<T> readLater(@NotNull ArgumentInput input) {
        return CompletableFuture.completedFuture(read(input));
    }

    /** The words that fit here, for commands and lists. {@link ArgumentInput#getText()} is what is already typed. */
    @NotNull
    default List<String> suggest(@NotNull ArgumentInput input) {
        return Collections.emptyList();
    }

    /** What {@code me} means for {@code viewer} (the one who looks), or {@code null}. */
    @Nullable
    default T meOf(@NotNull ViewerInfo viewer) {
        return null;
    }
}
