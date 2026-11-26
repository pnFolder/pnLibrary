package org.pnfolder.pnlibrary.api.placeholders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;

/** The value of one placeholder registered without a class; see {@link PlaceholderService#value}. */
@FunctionalInterface
public interface PlaceholderValue {
    /** The value for {@code viewer}, or {@code null} when there is none. */
    @Nullable
    Object get(@NotNull ViewerInfo viewer);
}
