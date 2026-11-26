package org.pnfolder.pnlibrary.core.guide;

import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** A titled group of command guide entries. */
public final class GuideSection {
    private final String title;
    private final List<GuideEntry> entries;

    /** Creates a section. */
    public GuideSection(@NotNull String title, @NotNull List<GuideEntry> entries) {
        this.title = Objects.requireNonNull(title, "title");
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /** The heading of the section. */
    public @NotNull String getTitle() {
        return title;
    }

    /** The entries of the section in print order. */
    public @NotNull List<GuideEntry> getEntries() {
        return entries;
    }
}
