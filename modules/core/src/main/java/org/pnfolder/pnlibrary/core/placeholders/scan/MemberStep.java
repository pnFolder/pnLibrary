package org.pnfolder.pnlibrary.core.placeholders.scan;

import org.pnfolder.pnlibrary.core.placeholders.Failures;
import org.pnfolder.pnlibrary.core.placeholders.Call;
import org.pnfolder.pnlibrary.core.placeholders.format.Formatted;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import org.pnfolder.pnlibrary.api.placeholders.NoValueException;
import org.pnfolder.pnlibrary.api.placeholders.reply.PlaceholderAnswer;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

/**
 * One member on the way of an address: takes what the step before gave, calls the member and turns its {@code Reply}
 * into a plain value, an object to go on with, or an early answer.
 */
final class MemberStep implements Function<Call, Object> {
    private final Member member;
    private final List<ParameterPlan> plans;
    private final Function<Call, Object> receiver;
    private final boolean leaf;

    MemberStep(Member member, List<ParameterPlan> plans, Function<Call, Object> receiver, boolean leaf) {
        this.member = member;
        this.plans = plans;
        this.receiver = receiver;
        this.leaf = leaf;
    }

    @Override
    public Object apply(final Call call) {
        Object source = receiver.apply(call);
        if (source == null) {
            return null;
        }
        if (source instanceof CompletionStage) {
            // The step before answers later: continue when it has. Every step below it waits the same way.
            return ((CompletionStage<?>) source).thenCompose(value -> {
                if (value == null) {
                    return CompletableFuture.completedFuture(null);
                }
                Object result = run(value, call);
                return result instanceof CompletionStage ? castStage(result) : CompletableFuture.completedFuture(result);
            });
        }
        return run(source, call);
    }

    @SuppressWarnings("unchecked")
    private static CompletionStage<Object> castStage(Object stage) {
        return (CompletionStage<Object>) stage;
    }

    private Object run(Object source, Call call) {
        Object[] arguments = new Object[plans.size()];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = plans.get(i).value(call);
        }
        Object result = invoke(source, arguments);
        return result instanceof CompletionStage ? castStage(result).thenApply(this::settle) : settle(result);
    }

    private Object invoke(Object source, Object[] arguments) {
        try {
            return member.read(source, arguments);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getTargetException();
            if (cause instanceof NoValueException) {
                return null;
            }
            throw Failures.unchecked(cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** Every method returns a plain value, an object, a Reply, or a CompletionStage of those; this makes one thing of it. */
    private Object settle(Object result) {
        if (!(result instanceof Reply)) {
            return result;
        }
        Reply<?> reply = (Reply<?>) result;
        switch (reply.getKind()) {
            case NONE:
                return null;
            case NEXT:
                return reply.getContent();
            case VALUE:
                if (leaf) {
                    return withFormat(reply.getContent(), reply.getFormat());
                }
                throw new PlaceholderAnswer(withFormat(reply.getContent(), reply.getFormat()));
            default:
                throw new PlaceholderAnswer(withFormat(reply.getContent(), reply.getFormat()));
        }
    }

    private static Object withFormat(Object content, String format) {
        return content != null && format != null ? new Formatted(content, format) : content;
    }
}
