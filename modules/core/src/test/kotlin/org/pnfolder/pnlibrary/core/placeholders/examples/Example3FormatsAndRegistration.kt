package org.pnfolder.pnlibrary.core.placeholders.examples

import org.pnfolder.pnlibrary.api.placeholders.Argument
import org.pnfolder.pnlibrary.api.placeholders.ArgumentValue
import org.pnfolder.pnlibrary.api.placeholders.Format
import org.pnfolder.pnlibrary.api.placeholders.Placeholder
import org.pnfolder.pnlibrary.api.placeholders.Placeholders
import org.pnfolder.pnlibrary.api.placeholders.Reply
import org.pnfolder.pnlibrary.api.placeholders.UseFormat
import org.pnfolder.pnlibrary.api.placeholders.Viewer
import org.pnfolder.pnlibrary.api.placeholders.ViewerInfo
import org.pnfolder.pnlibrary.api.plugin.ModuleContext

// Example 3: formats, who is asking, and how to connect everything.

/** Formats are methods marked with @Format. `{clan.level|rank}` gives #12, `{clan.level|pad0:4}` gives 0012. */
internal class ClanFormats {

    /** The first parameter is the value; the format is used as `|rank`. */
    @Format("rank")
    fun rank(n: Int): String = "#$n"

    /** Parameters after the first are what is written after the colon: `|pad0:4`. */
    @Format("pad0")
    fun pad0(n: Int, @Argument width: ArgumentValue<Int>): String = n.toString().padStart(width.required(), '0')

    /** A format that knows nothing but the number: `|short` gives 1,3M. */
    @Format("money")
    fun money(n: Long): String = "%,d".format(n).replace(',', ' ') + " $"
}

/** A default format needs no `|name` in the text. On a method it covers one value, on a class every value of the type. */
@Placeholders("bank")
@UseFormat("money", type = Long::class)
internal class ClanBank {
    @Placeholder
    fun balance(@Viewer viewer: ViewerInfo): Reply<Long> = Reply.of(Clans.of(viewer.playerId)?.balance)

    /** The text can still name another format (`{bank.bonus|rank}`) or turn the default off (`{bank.bonus|raw}`). */
    @Placeholder
    @UseFormat("rank")
    fun bonus(@Viewer viewer: ViewerInfo): Reply<Int> = Reply.of(3)
}

/** Who asks. Language and the way the text was asked for are known; the player may be missing. */
@Placeholders("who")
internal class AboutTheViewer {
    @Placeholder
    fun hello(@Viewer viewer: ViewerInfo): Reply<String> = Reply.of(if (viewer.language == "ru") "Привет" else "Hello")

    /** `viewer.requirePlayerId()` ends the placeholder quietly when there is no player (console, background work). */
    @Placeholder
    fun id(@Viewer viewer: ViewerInfo): Reply<String> = Reply.of(viewer.requirePlayerId().toString())
}

/** Connecting everything in the `onEnable` of a module. Closing the module releases it all. */
internal fun ModuleContext.setupPlaceholders() {
    // The converter and the formats belong to this module and go away with it.
    placeholders.converter(Clan::class.java, ClanByName())
    placeholders.formats(ClanFormats())
    placeholders.register(ClanBasics(), ClanArguments(), ClanBank(), AboutTheViewer(), papi = "pnclans")
}
