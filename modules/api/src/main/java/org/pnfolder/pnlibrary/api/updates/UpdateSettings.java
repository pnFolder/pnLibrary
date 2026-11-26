package org.pnfolder.pnlibrary.api.updates;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;

/**
 * What an administrator chooses for a product. A choice is remembered across restarts and wins over what the plugin
 * declares; removing it hands the decision back to the plugin. Each change is recorded with its {@link UpdateActor}.
 */
public interface UpdateSettings {
    /** Switches {@code product} to the release {@code channel} now; {@code null} removes the choice. */
    @NotNull
    default CompletionStage<UpdateSnapshot> channel(@NotNull String product, @Nullable UpdateChannel channel) {
        return channel(product, channel, UpdateActor.UNKNOWN);
    }

    /** Switches {@code product} to the release {@code channel} now, recorded for {@code actor}. */
    @NotNull
    CompletionStage<UpdateSnapshot> channel(@NotNull String product, @Nullable UpdateChannel channel,
                                            @NotNull UpdateActor actor);

    /** Holds the updates of {@code product} for five days; see {@link #hold(String, Duration, UpdateActor)}. */
    @NotNull
    default CompletionStage<UpdateSnapshot> hold(@NotNull String product) {
        return hold(product, Duration.ofDays(5), UpdateActor.UNKNOWN);
    }

    /** Holds the updates of {@code product} for {@code duration}; see {@link #hold(String, Duration, UpdateActor)}. */
    @NotNull
    default CompletionStage<UpdateSnapshot> hold(@NotNull String product, @NotNull Duration duration) {
        return hold(product, duration, UpdateActor.UNKNOWN);
    }

    /**
     * Holds the updates of {@code product} for {@code duration}, from one minute to five days, replacing an earlier
     * hold: nothing is offered or downloaded until the hold ends by itself or {@link #release} is called.
     */
    @NotNull
    CompletionStage<UpdateSnapshot> hold(@NotNull String product, @NotNull Duration duration,
                                         @NotNull UpdateActor actor);

    /** Ends the hold of {@code product} at once. */
    @NotNull
    default CompletionStage<UpdateSnapshot> release(@NotNull String product) {
        return release(product, UpdateActor.UNKNOWN);
    }

    /** Ends the hold of {@code product} at once, recorded for {@code actor}. */
    @NotNull
    CompletionStage<UpdateSnapshot> release(@NotNull String product, @NotNull UpdateActor actor);

    /** When the hold of {@code product} ends, or {@code null} when its updates are not held. */
    @Nullable
    Instant heldUntil(@NotNull String product);

    /**
     * Chooses which Java build of {@code product} is installed when its release has several, such as one for Java 8
     * and one for Java 17. {@code java} must be one of {@link UpdateSnapshot#getJavaBuilds()}; {@code null} removes
     * the choice, so the build that matches the installed product (else the newest the server can run) is used.
     */
    @NotNull
    default CompletionStage<UpdateSnapshot> javaBuild(@NotNull String product, @Nullable Integer java) {
        return javaBuild(product, java, UpdateActor.UNKNOWN);
    }

    /** Chooses the Java build of {@code product}, recorded for {@code actor}. */
    @NotNull
    CompletionStage<UpdateSnapshot> javaBuild(@NotNull String product, @Nullable Integer java,
                                              @NotNull UpdateActor actor);
}
