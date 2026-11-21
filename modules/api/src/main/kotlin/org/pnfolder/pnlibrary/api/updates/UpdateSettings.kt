package org.pnfolder.pnlibrary.api.updates

import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletionStage

/**
 * What an administrator chooses for a product. A choice is remembered across restarts and wins over what the plugin
 * declares; removing it hands the decision back to the plugin. Each change is recorded with its [UpdateActor].
 */
interface UpdateSettings {
    /** Switches [product] to the release [channel] now; `null` removes the choice, so the plugin's own channel applies. */
    fun channel(product: String, channel: UpdateChannel?, actor: UpdateActor = UpdateActor.UNKNOWN): CompletionStage<UpdateSnapshot>

    /** Turns automatic download of verified updates of [product] on or off. */
    fun automaticDownload(product: String, enabled: Boolean, actor: UpdateActor = UpdateActor.UNKNOWN): CompletionStage<UpdateSnapshot>

    /** Turns automatic download on or off for every plugin, also those registered later; single choices are dropped. */
    fun automaticDownloadForAll(enabled: Boolean, actor: UpdateActor = UpdateActor.UNKNOWN): CompletionStage<List<UpdateSnapshot>>

    /**
     * Holds the updates of [product] for [duration], from one minute to seven days, replacing an earlier hold: nothing is
     * offered or downloaded until the hold ends by itself or [release] is called.
     */
    fun hold(product: String, duration: Duration = Duration.ofDays(7), actor: UpdateActor = UpdateActor.UNKNOWN): CompletionStage<UpdateSnapshot>

    /** Ends the hold of [product] at once. */
    fun release(product: String, actor: UpdateActor = UpdateActor.UNKNOWN): CompletionStage<UpdateSnapshot>

    /** When the hold of [product] ends, or `null` when its updates are not held. */
    fun heldUntil(product: String): Instant?

    /**
     * Chooses which Java build of [product] is installed when its release has several, such as one for Java 8 and one for
     * Java 17. [java] must be one of [UpdateSnapshot.javaBuilds]; `null` removes the choice, so the build that matches the
     * installed product (else the newest the server can run) is used.
     */
    fun javaBuild(product: String, java: Int?, actor: UpdateActor = UpdateActor.UNKNOWN): CompletionStage<UpdateSnapshot>
}
