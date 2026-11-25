package org.pnfolder.pnlibrary.core.placeholders.examples

import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter
import org.pnfolder.pnlibrary.api.placeholders.reply.PublishedAnswers
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo
import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import java.util.UUID

// Example 5: asking for values. Registration is in example 3; here the module is already set up.

/** Everything the service can answer, in the order a plugin usually needs it. */
internal class UsingThePlaceholders(private val context: ModuleContext) {
    private val placeholders = context.placeholders

    /** Text with several addresses: each `{...}` is replaced, an unknown one stays as written. */
    fun message(player: UUID): java.util.concurrent.CompletionStage<String> =
        placeholders.render("Clan {clan.level}, top {stats.top.1}, balance {bank.balance}", player)

    /** Values of the caller win over registered ones: `{reward}` is not a placeholder, but it is filled. */
    fun withOwnData(player: UUID): java.util.concurrent.CompletionStage<String> =
        placeholders.render("You get {reward} for {clan.name}", ViewerInfo.of(player), mapOf("reward" to 100))

    /** One value, not text: the type stays (Int here), so it can be compared or calculated. */
    fun level(player: UUID): java.util.concurrent.CompletionStage<Any?> = placeholders.resolve("clan.level", player)

    /** Known or not, without evaluating anything: for commands that check an address the player typed. */
    fun known(address: String): Boolean = placeholders.contains(address)

    /** What is registered, with description, owner, type, default format and PlaceholderAPI names. */
    fun describe(): List<String> = placeholders.list().map {
        "{${it.address}} ${it.type} ${it.description} (${it.owner}) ${it.published.joinToString()}"
    }

    /** The outside systems. `%...%` of PlaceholderAPI is filled by `render` itself when an adapter is available. */
    fun outside(): List<String> = placeholders.adapters().all().map { "${it.id}: ${if (it.available) "available" else "unavailable"}" }

    /** Closing the handle removes the adapter; closing the module removes everything else. */
    fun connectOwnSystem(): AutoCloseable = placeholders.adapters().register(OwnSystem)
}

/** An outside placeholder system, such as PlaceholderAPI. The library has one for it; this one shows the shape. */
internal object OwnSystem : PlaceholderAdapter {
    override val id = "own-system"
    override val available = true

    /** `{own-system:time}`-like expressions the library does not know are asked here. */
    override fun resolve(playerId: UUID?, expression: String): Any? = if (expression == "time") "12:00" else null

    /** Fills `%tokens%` of a finished text. */
    override fun render(playerId: UUID?, text: String): String = text.replace("%time%", "12:00")

    /** Shows the library's placeholders to the system under [prefix]; the handle takes them back. */
    override fun publish(prefix: String, answers: PublishedAnswers): AutoCloseable = AutoCloseable { }
}
