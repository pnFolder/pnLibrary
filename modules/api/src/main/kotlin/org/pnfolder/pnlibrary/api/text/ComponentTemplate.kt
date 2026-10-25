package org.pnfolder.pnlibrary.api.text

import net.kyori.adventure.text.Component
import java.util.UUID
import java.util.concurrent.CompletionStage

/** Mutable rendering invocation created from one serialized component template. */
interface ComponentTemplate {

    /** Associates a named template token with [value]. */
    fun value(name: String, value: Any?): ComponentTemplate

    /** Selects the player used by player-aware placeholder resolution. */
    fun player(playerId: UUID): ComponentTemplate

    /** Resolves configured values and placeholders into the final component without blocking the caller. */
    fun renderAsync(): CompletionStage<Component>

    /** Like [renderAsync], but waits for the result; do not call it from a thread that placeholders may need. */
    fun render(): Component = renderAsync().toCompletableFuture().join()
}
