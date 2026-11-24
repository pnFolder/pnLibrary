package org.pnfolder.pnlibrary.core.placeholders.examples

import java.util.UUID

// The small "clans" world the examples talk about. A real plugin has its own classes instead.

internal class WarInfo(val score: Int, val wins: Int)

internal class Member(val name: String, val kills: Int)

internal class Clan(val name: String, val level: Int, val balance: Long, val war: WarInfo, val members: List<Member>)

internal object Clans {
    private val wolves = Clan("Wolves", 12, 1_250_000, WarInfo(340, 2), listOf(Member("Alex", 47), Member("Mia", 31)))
    private val owls = Clan("Owls", 4, 80_000, WarInfo(90, 0), listOf(Member("Bob", 5)))
    private val byPlayer = mutableMapOf<UUID, Clan>()

    fun join(player: UUID, clan: Clan) {
        byPlayer[player] = clan
    }

    fun of(player: UUID?): Clan? = player?.let(byPlayer::get)

    fun byName(text: String): Clan? = listOf(wolves, owls).firstOrNull { it.name.equals(text, ignoreCase = true) }

    fun names(): List<String> = listOf(wolves, owls).map { it.name }

    fun wolves(): Clan = wolves
}

internal enum class Period { DAY, WEEK, MONTH }
