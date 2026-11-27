package org.pnfolder.pnlibrary.core.commands.engine;

import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;

/** Paging of long chat lists: how many pages there are, which slice a page shows, and the "back / next" line. */
public final class ChatPager {
    private ChatPager() {
    }

    /** Number of pages for {@code total} rows; never below one so an empty list still has a first page. */
    public static int pageCount(int total, int pageSize) {
        return Math.max((total + pageSize - 1) / pageSize, 1);
    }

    /** Keeps {@code requested} inside {@code 1..pages}. */
    public static int clamp(int requested, int pages) {
        return Math.max(1, Math.min(requested, pages));
    }

    /** The rows that belong to {@code page} (counted from 1). */
    @NotNull
    public static <T> List<T> slice(@NotNull List<T> all, int page, int pageSize) {
        int from = (page - 1) * pageSize;
        if (from >= all.size()) {
            return Collections.emptyList();
        }
        return all.subList(from, Math.min(all.size(), from + pageSize));
    }

    /** The line with "back" and "next" buttons under a page; {@code base} is the command without the page number. */
    @NotNull
    public static Component pager(@NotNull ChatKit kit, @NotNull String base, int page, int pages) {
        Component line = kit.text("  ");
        line = line.append(page > 1
                ? link(kit, base, page - 1, pages, "‹ назад")
                : kit.text("[‹ назад]", ChatPalette.FAINT));
        line = line.append(kit.text("  " + page + " / " + pages + "  ", ChatPalette.ACCENT));
        return line.append(page < pages
                ? link(kit, base, page + 1, pages, "дальше ›")
                : kit.text("[дальше ›]", ChatPalette.FAINT));
    }

    private static Component link(ChatKit kit, String base, int target, int pages, String label) {
        return kit.option(label, base + " " + target, "Страница " + target + " из " + pages, ChatPalette.SOFT);
    }
}
