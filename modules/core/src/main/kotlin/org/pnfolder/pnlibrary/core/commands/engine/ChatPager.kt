package org.pnfolder.pnlibrary.core.commands.engine

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette

internal fun pageCount(total: Int, pageSize: Int): Int = ((total + pageSize - 1) / pageSize).coerceAtLeast(1)

internal fun chatPager(kit: ChatKit, base: String, page: Int, pages: Int): Component {
    fun link(target: Int, label: String) = kit.option(label, "$base $target", "Страница $target из $pages", ChatPalette.SOFT)
    var line = kit.text("  ")
    line = line.append(if (page > 1) link(page - 1, "‹ назад") else kit.text("[‹ назад]", ChatPalette.FAINT))
    line = line.append(kit.text("  $page / $pages  ", ChatPalette.ACCENT))
    return line.append(if (page < pages) link(page + 1, "дальше ›") else kit.text("[дальше ›]", ChatPalette.FAINT))
}
