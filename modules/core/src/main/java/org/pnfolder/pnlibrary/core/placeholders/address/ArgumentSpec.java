package org.pnfolder.pnlibrary.core.placeholders.address;

import java.util.Objects;
import org.pnfolder.pnlibrary.core.placeholders.SlotType;
import org.pnfolder.pnlibrary.core.placeholders.Failures;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentInput;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentRejected;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.placeholders.route.RouteInfo;
import org.pnfolder.pnlibrary.api.placeholders.route.RoutePart;

/** What {@code ArgumentValue<T>} of one parameter needs to turn text into a value; built once when the class is scanned. */
public final class ArgumentSpec {
    private final ArgumentConverter<Object> converter;
    private final SlotType pathType;

    ArgumentSpec(@NotNull ArgumentConverter<Object> converter) {
        this.converter = Objects.requireNonNull(converter, "converter");
        ArgumentInput blank = new ArgumentInput("", ViewerInfo.none(), new RouteInfo(Collections.emptyList()), "", 0,
                Collections.emptyList());
        this.pathType = new SlotType(converter.getLabel(), converter.suggest(blank), input -> make(input).thenApply(
                value -> new ParsedArgument(input.getText(), value)));
    }

    private ArgumentValue<Object> finish(ArgumentInput input, Object value, String rejected) {
        String error = null;
        if (value == null) {
            error = rejected != null ? rejected : "'" + input.getText() + "' is not valid: expected " + converter.getLabel();
        }
        return new ArgumentValue<>(input.getText(), value, error, viewer -> converter.meOf(viewer));
    }

    /**
     * At once, for formats. Always gives an {@link ArgumentValue}: its value is {@code null} and its error says why when
     * the text does not fit.
     */
    public ArgumentValue<Object> makeNow(ArgumentInput input) {
        try {
            return finish(input, converter.read(input), null);
        } catch (ArgumentRejected rejected) {
            return finish(input, null, rejected.getReason());
        }
    }

    /** The same, through the converter's {@code readLater}. */
    CompletionStage<ArgumentValue<Object>> make(ArgumentInput input) {
        CompletionStage<Object> stage;
        try {
            stage = converter.readLater(input);
        } catch (ArgumentRejected rejected) {
            return CompletableFuture.completedFuture(finish(input, null, rejected.getReason()));
        }
        return stage.handle((value, failure) -> {
            ArgumentRejected rejected = rejectionOf(failure);
            if (failure != null && rejected == null) {
                throw Failures.unchecked(failure);
            }
            return finish(input, value, rejected == null ? null : rejected.getReason());
        });
    }

    private static ArgumentRejected rejectionOf(Throwable failure) {
        if (failure instanceof ArgumentRejected) {
            return (ArgumentRejected) failure;
        }
        if (failure != null && failure.getCause() instanceof ArgumentRejected) {
            return (ArgumentRejected) failure.getCause();
        }
        return null;
    }

    /** The path part used by the placeholder tree. It never rejects a text, so the method always sees what was written. */
    public SlotType getPathType() {
        return pathType;
    }
}
