package org.pnfolder.pnlibrary.api.scenario.actions.builtin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionTarget;
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializerType;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Sends chat text; the lines become one message, one under another. */
public final class MessageAction implements Action {
    private final List<String> messages;
    private final ComponentSerializerType serializerType;
    private final ActionTarget target;

    /** Creates the action with every setting at its default. */
    public MessageAction() {
        this(Collections.<String>emptyList(), null, ActionTarget.PLAYER);
    }

    /** Creates it with the first 1 setting; the rest keep their defaults. */
    public MessageAction(@NotNull List<String> messages) {
        this(messages, null, ActionTarget.PLAYER);
    }

    /** Creates it with the first 2 settings; the rest keep their defaults. */
    public MessageAction(@NotNull List<String> messages, @Nullable ComponentSerializerType serializerType) {
        this(messages, serializerType, ActionTarget.PLAYER);
    }

    /** Creates it with every setting given. */
    public MessageAction(@NotNull List<String> messages, @Nullable ComponentSerializerType serializerType, @NotNull ActionTarget target) {
        this.messages = messages;
        this.serializerType = serializerType;
        this.target = target;
    }

    /** Text lines; placeholders are filled in. */
    @NotNull
    public List<String> getMessages() {
        return messages;
    }

    /** Text format override, or {@code null} for the default. */
    @Nullable
    public ComponentSerializerType getSerializerType() {
        return serializerType;
    }

    /** Who receives it. */
    @NotNull
    public ActionTarget getTarget() {
        return target;
    }

    @NotNull
    @Override
    public ActionResult run(@NotNull ActionContext context) {
        context.target(target).sendMessage(context.component(messages, serializerType));
        return ActionResult.Done.INSTANCE;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MessageAction)) {
            return false;
        }
        MessageAction that = (MessageAction) other;
        return Objects.equals(messages, that.messages)
                && Objects.equals(serializerType, that.serializerType)
                && Objects.equals(target, that.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messages, serializerType, target);
    }

    @NotNull
    @Override
    public String toString() {
        return "MessageAction(messages=" + messages + ", serializerType=" + serializerType + ", target=" + target + ")";
    }
}
