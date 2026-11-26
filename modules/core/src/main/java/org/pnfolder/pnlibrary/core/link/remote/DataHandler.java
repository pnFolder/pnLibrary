package org.pnfolder.pnlibrary.core.link.remote;

import java.util.function.BiConsumer;
import org.jetbrains.annotations.NotNull;

/** Answers the questions other servers ask of this one. */
public interface DataHandler {
    /** Answers {@code call}; {@code reply} takes whether it worked and the answer, and may be called from any thread. */
    void handle(@NotNull DataCall call, @NotNull BiConsumer<Boolean, String> reply);
}
