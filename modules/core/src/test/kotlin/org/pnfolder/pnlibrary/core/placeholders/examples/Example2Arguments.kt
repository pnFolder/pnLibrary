package org.pnfolder.pnlibrary.core.placeholders.examples

import org.pnfolder.pnlibrary.api.placeholders.Argument
import org.pnfolder.pnlibrary.api.placeholders.ArgumentValue
import org.pnfolder.pnlibrary.api.placeholders.Group
import org.pnfolder.pnlibrary.api.placeholders.Invalid
import org.pnfolder.pnlibrary.api.placeholders.Placeholder
import org.pnfolder.pnlibrary.api.placeholders.Placeholders
import org.pnfolder.pnlibrary.api.placeholders.PlayerRef
import org.pnfolder.pnlibrary.api.placeholders.Reply
import org.pnfolder.pnlibrary.api.placeholders.Viewer
import org.pnfolder.pnlibrary.api.placeholders.ViewerInfo
import java.time.Duration

// Example 2: arguments.
//
// In an address there is only text. A parameter of type ArgumentValue<T> receives it converted to T.
// The ArgumentValue keeps the text too, so the method can still answer when the conversion failed.

@Placeholders("stats")
internal class ClanArguments {

    /** `{stats.top.3}`. A number outside 1..100 is not a value, and the address stays as it is. */
    @Placeholder
    fun top(@Argument(range = "1..100") place: ArgumentValue<Int>): Reply<String> =
        Reply.of(Clans.wolves().members.sortedByDescending { it.kills }.getOrNull(place.required() - 1)?.name)

    /** `{stats.kills.Alex.week}`. Words of an enum are offered and checked by themselves. */
    @Placeholder
    fun kills(@Argument who: ArgumentValue<PlayerRef>, @Argument period: ArgumentValue<Period>): Reply<Int> =
        Reply.of(Clans.wolves().members.first { it.name.equals(who.required().text, true) }.kills * if (period.required() == Period.WEEK) 1 else 4)

    /** `{stats.sort.kills}`. A fixed list of words for text. */
    @Placeholder
    fun sort(@Argument(choices = ["kills", "name"]) by: ArgumentValue<String>): Reply<String> =
        Reply.of(Clans.wolves().members.sortedBy { if (by.required() == "kills") -it.kills.toLong() else it.name.hashCode().toLong() }.first().name)

    /** `{stats.friend.Bob}`. The player is not known: the method shows its own answer instead of failing. */
    @Placeholder
    fun friend(@Argument who: ArgumentValue<PlayerRef>): Reply<String> =
        Reply.of(Clans.wolves().members.firstOrNull { it.name.equals(who.text, true) }?.name ?: "Nobody called ${who.text}")

    /** `{stats.mine.me}` is the one who looks, `{stats.mine.Mia}` is Mia. */
    @Placeholder
    fun mine(@Argument who: ArgumentValue<PlayerRef>, @Viewer viewer: ViewerInfo): Reply<String> = Reply.of(who.orMe(viewer)?.text)

    /** `{stats.age.1h30m}`. Durations are written as 90s, 15m, 1h30m or 2d. */
    @Placeholder
    fun age(@Argument span: ArgumentValue<Duration>): Reply<Long> = Reply.of(span.required().seconds)

    /** `{stats.find.wol}`. Plain text is not converted: the method searches by itself. */
    @Placeholder
    fun find(@Argument query: ArgumentValue<String>): Reply<String> =
        Reply.of(listOf("Wolves", "Owls").firstOrNull { it.contains(query.text, ignoreCase = true) })

    /** `{stats.own.Wolves}`. A type of the plugin works too once [registerClanArgument] has run. */
    @Placeholder
    fun own(@Argument clan: ArgumentValue<Clan>): Reply<Int> = Reply.of(clan.required().members.size)

    /** When there is no value, show a fixed text instead of leaving the address in the text. */
    @Placeholder(onInvalid = Invalid.DEFAULT, default = "no clan")
    fun clanOf(@Argument clan: ArgumentValue<Clan>): Reply<String> = Reply.of(clan.value?.name)

    /** Several arguments in one class, so that the signature stays short: `{stats.query.Alex.week}`. */
    @Placeholder
    fun query(@Group query: KillsQuery): Reply<String> = Reply.of("${query.who.required().text}-${query.period.required()}")
}

/** No annotation on the class: the parameter that takes it is marked with `@Group`. */
internal class KillsQuery(@Argument val who: ArgumentValue<PlayerRef>, @Argument val period: ArgumentValue<Period>)
