package org.pnfolder.pnlibrary.api.placeholders.examples

import org.pnfolder.pnlibrary.api.placeholders.Invalid
import org.pnfolder.pnlibrary.api.placeholders.Placeholder
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderValue
import org.pnfolder.pnlibrary.api.placeholders.Placeholders
import org.pnfolder.pnlibrary.api.placeholders.Reply
import org.pnfolder.pnlibrary.api.placeholders.Viewer
import org.pnfolder.pnlibrary.api.placeholders.ViewerInfo
import java.time.Duration
import java.util.concurrent.CompletableFuture

// Example 4: what a method may return, and placeholders that have no class.

/** `{server.*}`: values that do not depend on who asks. */
@Placeholders("server")
internal class ServerValues {

    /** A number, text, boolean, enum, UUID or [Duration] works as it is. `{server.online}` gives 3. */
    @Placeholder("Players online")
    fun online(): Reply<Int> = Reply.of(3)

    /** A [Duration] is shown by the format `left` or `time`: `{server.restart|left}`. */
    @Placeholder("Time until the restart")
    fun restart(): Reply<Duration> = Reply.of(Duration.ofMinutes(95))

    /** A value that takes time may come later: return a CompletableFuture, the caller is not blocked. */
    @Placeholder("A value calculated in the background")
    fun background(): CompletableFuture<Reply<Long>> = (CompletableFuture.supplyAsync { 42L }).thenApply { reply -> Reply.of(reply) }

    /** A nested object: `{server.owner.name}` and `{server.owner.since}`. */
    @Placeholder("The owner of the server")
    fun owner(): Reply<Owner> = Reply.next(Owner("Steve", 2020))

    /** A branch may come later too: `{server.stats.cpu}`. The next methods of the address wait for it, nobody is blocked. */
    @Placeholder("Numbers from the database")
    fun stats(): CompletableFuture<Reply<ServerStats>> = CompletableFuture.supplyAsync { Reply.next(ServerStats()) }

    /** Without a value the text can show nothing instead of the address: `Invalid.EMPTY`. */
    @Placeholder(onInvalid = Invalid.EMPTY)
    fun motd(): Reply<String> = Reply.of(null)

    /** Or a fixed text: `Invalid.DEFAULT` shows [Placeholder.default]. */
    @Placeholder(onInvalid = Invalid.DEFAULT, default = "unknown")
    fun region(): Reply<String> = Reply.of(null)
}

internal class ServerStats {
    @Placeholder val cpu: Reply<Int> get() = Reply.of(17)

    @Placeholder val memory: Reply<Int> get() = Reply.of(42)
}

internal class Owner(private val ownerName: String, private val ownerSince: Int) {
    @Placeholder("Name of the owner")
    val name: Reply<String> get() = Reply.of(ownerName)

    @Placeholder("Since which year")
    val since: Reply<Int> get() = Reply.of(ownerSince)
}

/** For whoever asks: [ViewerInfo] is filled by the library and is not a part of the address. */
@Placeholders("me")
internal class MeValues {

    /** `{me.language}` gives `ru` or `en`. */
    @Placeholder
    fun language(@Viewer viewer: ViewerInfo): Reply<String> = Reply.of(viewer.language)

    /** The player may be missing (console, background work), so the value is nullable: `{me.id}` stays as written. */
    @Placeholder
    fun id(@Viewer viewer: ViewerInfo): Reply<String> = Reply.of(viewer.playerId?.toString())

    /** Asked through PlaceholderAPI (`%pn_me_papi%`) or not. */
    @Placeholder
    fun papi(@Viewer viewer: ViewerInfo): Reply<Boolean> = Reply.of(viewer.viaPapi)
}

/** Names known only while running: one placeholder per currency, per setting, per world. No class is needed. */
internal fun registerDynamic(placeholders: PlaceholderService, currencies: List<String>): List<AutoCloseable> =
    currencies.map { name ->
        // `{wallet.gold}`; with papi set it is also `%pn_wallet_gold%`. The returned handle removes it.
        placeholders.value("wallet.$name", "Balance in $name", "pn", PlaceholderValue { viewer -> balanceOf(viewer, name) })
    }

private fun balanceOf(@Viewer viewer: ViewerInfo, currency: String): Long? = viewer.playerId?.let { currency.length * 100L }
