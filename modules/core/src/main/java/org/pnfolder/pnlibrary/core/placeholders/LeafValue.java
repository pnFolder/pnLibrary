package org.pnfolder.pnlibrary.core.placeholders;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import org.pnfolder.pnlibrary.api.placeholders.NoValueException;
import org.pnfolder.pnlibrary.api.placeholders.reply.PlaceholderAnswer;

/** The value of a placeholder: runs the steps of its address and puts the fallback in place of a missing value. */
final class LeafValue implements Function<Call, CompletionStage<Object>> {
    private final Function<Call, Object> step;
    private final Object fallback;

    LeafValue(Function<Call, Object> step, Object fallback) {
        this.step = step;
        this.fallback = fallback;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Object> apply(Call call) {
        Object result;
        try {
            result = step.apply(call);
        } catch (NoValueException ignored) {
            result = null;
        } catch (PlaceholderAnswer answered) {
            return CompletableFuture.completedFuture(answered.getValue());
        }
        if (!(result instanceof CompletionStage)) {
            return CompletableFuture.completedFuture(result != null ? result : fallback);
        }
        return ((CompletionStage<Object>) result).handle((value, failure) -> {
            PlaceholderAnswer answered = answerOf(failure);
            if (answered != null) {
                return answered.getValue();
            }
            if (failure != null && !(failure instanceof NoValueException) && !(failure.getCause() instanceof NoValueException)) {
                throw Failures.unchecked(failure);
            }
            return value != null ? value : fallback;
        });
    }

    private static PlaceholderAnswer answerOf(Throwable failure) {
        if (failure instanceof PlaceholderAnswer) {
            return (PlaceholderAnswer) failure;
        }
        if (failure != null && failure.getCause() instanceof PlaceholderAnswer) {
            return (PlaceholderAnswer) failure.getCause();
        }
        return null;
    }
}
