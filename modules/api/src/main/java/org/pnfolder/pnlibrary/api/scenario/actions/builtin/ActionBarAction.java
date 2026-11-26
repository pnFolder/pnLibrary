package org.pnfolder.pnlibrary.api.scenario.actions.builtin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionTarget;
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializerType;

import java.util.Objects;

/** Shows text above the hotbar. */
public final class ActionBarAction implements Action {
    private final String text;
    private final ComponentSerializerType serializerType;
    private final ActionTarget target;

    /** Creates the action with every setting at its default. */
    public ActionBarAction() {
        this("", null, ActionTarget.PLAYER);
    }

    /** Creates it with the first 1 setting; the rest keep their defaults. */
    public ActionBarAction(@NotNull String text) {
        this(text, null, ActionTarget.PLAYER);
    }

    /** Creates it with the first 2 settings; the rest keep their defaults. */
    public ActionBarAction(@NotNull String text, @Nullable ComponentSerializerType serializerType) {
        this(text, serializerType, ActionTarget.PLAYER);
    }

    /** Creates it with every setting given. */
    public ActionBarAction(@NotNull String text, @Nullable ComponentSerializerType serializerType, @NotNull ActionTarget target) {
        this.text = text;
        this.serializerType = serializerType;
        this.target = target;
    }

    /** The line; placeholders are filled in. */
    @NotNull
    public String getText() {
        return text;
    }

    /** Text format override, or {@code null} for the default. */
    @Nullable
    public ComponentSerializerType getSerializerType() {
        return serializerType;
    }

    /** Who sees it. */
    @NotNull
    public ActionTarget getTarget() {
        return target;
    }

    @NotNull
    @Override
    public ActionResult run(@NotNull ActionContext context) {
        context.target(target).sendActionBar(context.component(text, serializerType));
        return ActionResult.Done.INSTANCE;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ActionBarAction)) {
            return false;
        }
        ActionBarAction that = (ActionBarAction) other;
        return Objects.equals(text, that.text)
                && Objects.equals(serializerType, that.serializerType)
                && Objects.equals(target, that.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, serializerType, target);
    }

    @NotNull
    @Override
    public String toString() {
        return "ActionBarAction(text=" + text + ", serializerType=" + serializerType + ", target=" + target + ")";
    }
}
