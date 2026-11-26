package org.pnfolder.pnlibrary.api.scenario.actions.builtin;

import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionTarget;
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializerType;

import java.time.Duration;
import java.util.Objects;

/** Shows a large title with a smaller subtitle. Platforms that cannot show titles show nothing. */
public final class TitleAction implements Action {
    private final String title;
    private final String subtitle;
    private final Duration fadeIn;
    private final Duration stay;
    private final Duration fadeOut;
    private final ComponentSerializerType serializerType;
    private final ActionTarget target;

    /** Creates the action with every setting at its default. */
    public TitleAction() {
        this("", "", Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000), null, ActionTarget.PLAYER);
    }

    /** Creates it with the first 1 setting; the rest keep their defaults. */
    public TitleAction(@NotNull String title) {
        this(title, "", Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000), null, ActionTarget.PLAYER);
    }

    /** Creates it with the first 2 settings; the rest keep their defaults. */
    public TitleAction(@NotNull String title, @NotNull String subtitle) {
        this(title, subtitle, Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000), null, ActionTarget.PLAYER);
    }

    /** Creates it with the first 3 settings; the rest keep their defaults. */
    public TitleAction(@NotNull String title, @NotNull String subtitle, @NotNull Duration fadeIn) {
        this(title, subtitle, fadeIn, Duration.ofMillis(3500), Duration.ofMillis(1000), null, ActionTarget.PLAYER);
    }

    /** Creates it with the first 4 settings; the rest keep their defaults. */
    public TitleAction(@NotNull String title, @NotNull String subtitle, @NotNull Duration fadeIn, @NotNull Duration stay) {
        this(title, subtitle, fadeIn, stay, Duration.ofMillis(1000), null, ActionTarget.PLAYER);
    }

    /** Creates it with the first 5 settings; the rest keep their defaults. */
    public TitleAction(@NotNull String title, @NotNull String subtitle, @NotNull Duration fadeIn, @NotNull Duration stay, @NotNull Duration fadeOut) {
        this(title, subtitle, fadeIn, stay, fadeOut, null, ActionTarget.PLAYER);
    }

    /** Creates it with the first 6 settings; the rest keep their defaults. */
    public TitleAction(@NotNull String title, @NotNull String subtitle, @NotNull Duration fadeIn, @NotNull Duration stay, @NotNull Duration fadeOut, @Nullable ComponentSerializerType serializerType) {
        this(title, subtitle, fadeIn, stay, fadeOut, serializerType, ActionTarget.PLAYER);
    }

    /** Creates it with every setting given. */
    public TitleAction(@NotNull String title, @NotNull String subtitle, @NotNull Duration fadeIn, @NotNull Duration stay, @NotNull Duration fadeOut, @Nullable ComponentSerializerType serializerType, @NotNull ActionTarget target) {
        if (fadeIn.isNegative() || stay.isNegative() || fadeOut.isNegative()) {
            throw new IllegalArgumentException("Title times must not be negative");
        }
        this.title = title;
        this.subtitle = subtitle;
        this.fadeIn = fadeIn;
        this.stay = stay;
        this.fadeOut = fadeOut;
        this.serializerType = serializerType;
        this.target = target;
    }

    /** Main line. */
    @NotNull
    public String getTitle() {
        return title;
    }

    /** Second line. */
    @NotNull
    public String getSubtitle() {
        return subtitle;
    }

    /** Time to appear. */
    @NotNull
    public Duration getFadeIn() {
        return fadeIn;
    }

    /** Time fully visible. */
    @NotNull
    public Duration getStay() {
        return stay;
    }

    /** Time to disappear. */
    @NotNull
    public Duration getFadeOut() {
        return fadeOut;
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
        Title shown = Title.title(
                context.component(title, serializerType),
                context.component(subtitle, serializerType),
                Title.Times.times(fadeIn, stay, fadeOut));
        context.target(target).showTitle(shown);
        return ActionResult.Done.INSTANCE;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TitleAction)) {
            return false;
        }
        TitleAction that = (TitleAction) other;
        return Objects.equals(title, that.title)
                && Objects.equals(subtitle, that.subtitle)
                && Objects.equals(fadeIn, that.fadeIn)
                && Objects.equals(stay, that.stay)
                && Objects.equals(fadeOut, that.fadeOut)
                && Objects.equals(serializerType, that.serializerType)
                && Objects.equals(target, that.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, subtitle, fadeIn, stay, fadeOut, serializerType, target);
    }

    @NotNull
    @Override
    public String toString() {
        return "TitleAction(title=" + title + ", subtitle=" + subtitle + ", fadeIn=" + fadeIn + ", stay=" + stay + ", fadeOut=" + fadeOut + ", serializerType=" + serializerType + ", target=" + target + ")";
    }
}
