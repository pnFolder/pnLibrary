package org.pnfolder.pnlibrary.core.placeholders.format;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;

/** What a format does: takes the value, the words after its colon and the viewer, gives the next value. */
@FunctionalInterface
public interface FormatApply {
    @Nullable Object apply(@NotNull Object value, @NotNull List<String> arguments, @NotNull ViewerInfo viewer) throws Exception;
}
