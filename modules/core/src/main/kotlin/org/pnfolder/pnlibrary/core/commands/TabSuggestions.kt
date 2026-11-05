package org.pnfolder.pnlibrary.core.commands

import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

/**
 * Waits for tab suggestions for at most [millis] and returns an empty list when they are late or failed.
 * The server asks on a thread that must not stall, so a slow suggestion provider costs the player one empty Tab
 * instead of a lagging tick or an error in the log.
 */
fun CompletionStage<List<String>>.suggestionsWithin(millis: Long): List<String> = try {
    toCompletableFuture().get(millis, TimeUnit.MILLISECONDS)
} catch (_: InterruptedException) {
    Thread.currentThread().interrupt()
    emptyList()
} catch (_: Exception) {
    emptyList()
}

/** How long the main thread of a game server may wait for tab suggestions. */
const val MAIN_THREAD_SUGGESTION_MILLIS = 50L

/** How long a proxy thread, which is not the tick loop, may wait for tab suggestions. */
const val PROXY_SUGGESTION_MILLIS = 1000L
