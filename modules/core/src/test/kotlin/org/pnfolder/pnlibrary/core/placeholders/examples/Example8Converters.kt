package org.pnfolder.pnlibrary.core.placeholders.examples

import org.pnfolder.pnlibrary.api.placeholders.Argument
import org.pnfolder.pnlibrary.api.placeholders.ArgumentConverter
import org.pnfolder.pnlibrary.api.placeholders.ArgumentInput
import org.pnfolder.pnlibrary.api.placeholders.ArgumentValue
import org.pnfolder.pnlibrary.api.placeholders.Placeholder
import org.pnfolder.pnlibrary.api.placeholders.Placeholders
import org.pnfolder.pnlibrary.api.placeholders.Reply
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

// Example 8: converters. They turn the text of one argument into an object, also when the class is not yours.
//
// Two ways to attach one:
//   1. On the parameter:  @Argument(converter = ClanByName::class) clan: ArgumentValue<Clan>
//   2. For a whole type:  placeholders.converter(Clan::class.java, ClanByName())  (used when the parameter names none;
//      it belongs to the module and is removed with it)

/** Reads a clan by its name. `Clan` stays as it is: the converter lives beside it. */
internal class ClanByName : ArgumentConverter<Clan> {
    override val label = "clan name"

    override fun read(input: ArgumentInput): Clan? {
        if (input.text.equals("me", ignoreCase = true)) return Clans.of(input.viewer.playerId) // `me`: the clan of who asks
        return Clans.byName(input.text) ?: input.fail("there is no clan called '${input.text}'") // the reason is kept in ArgumentValue.error
    }

    /** For commands and lists: the words that fit what is typed so far. */
    override fun suggest(input: ArgumentInput): List<String> = Clans.names().filter { it.startsWith(input.text, ignoreCase = true) }
}

/** The same type read another way: by a tag of three letters. */
internal class ClanByTag : ArgumentConverter<Clan> {
    override val label = "clan tag"

    override fun read(input: ArgumentInput): Clan? = Clans.names().firstNotNullOfOrNull { Clans.byName(it) }
        ?.takeIf { it.name.take(3).equals(input.text, ignoreCase = true) }
}

/** Work that takes time (a database): override `readLater`. `read` is then not used. */
internal class ClanFromDatabase : ArgumentConverter<Clan> {
    override val label = "clan from the database"

    override fun read(input: ArgumentInput): Clan? = null

    override fun readLater(input: ArgumentInput): CompletionStage<Clan?> = CompletableFuture.supplyAsync { Clans.byName(input.text) }
}

/** Looks at what stands before it and at the whole address. `{clan.compare.Wolves.Owls}`: the second reads the first. */
internal class OtherClan : ArgumentConverter<Clan> {
    override val label = "another clan"

    override fun read(input: ArgumentInput): Clan? {
        val first = input.previous.firstOrNull()?.text // "Wolves": the arguments to the left
        val field = input.route.field?.text // "compare": the asked field
        val place = input.position // 1: the second argument of the address
        if (first.equals(input.text, ignoreCase = true)) input.fail("$field needs two different clans ($place)")
        return Clans.byName(input.text)
    }
}

@Placeholders("clanlist")
internal class ClanConverterValues {

    /** `{clanlist.members.Wolves}`. */
    @Placeholder
    fun members(@Argument(converter = ClanByName::class) clan: ArgumentValue<Clan>): Reply<Int> = Reply.of(clan.required().members.size)

    /** `{clanlist.level.Wol}`: the same type, read by tag. */
    @Placeholder
    fun level(@Argument(converter = ClanByTag::class) clan: ArgumentValue<Clan>): Reply<Int> = Reply.of(clan.required().level)

    /** `{clanlist.later.Wolves}`. The answer is calculated in the background. */
    @Placeholder
    fun later(@Argument(converter = ClanFromDatabase::class) clan: ArgumentValue<Clan>): Reply<Long> = Reply.of(clan.required().balance)

    /** A text that does not fit is not lost: the method sees the reason and may answer by itself. */
    @Placeholder
    fun known(@Argument(converter = ClanByName::class) clan: ArgumentValue<Clan>): Reply<String> = Reply.of(clan.value?.name ?: (clan.error ?: "unknown"))

    /** `{clanlist.compare.Wolves.Owls}`. */
    @Placeholder
    fun compare(
        @Argument(name = "first", converter = ClanByName::class) first: ArgumentValue<Clan>,
        @Argument(name = "second", converter = OtherClan::class) second: ArgumentValue<Clan>,
    ): Reply<Int> = Reply.of(first.required().level - second.required().level)
}
