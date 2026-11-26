package org.pnfolder.pnlibrary.core.placeholders;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.placeholders.info.FormatInfo;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.api.registry.RegistryEntry;

/** Shows placeholders and formats as entries of the library registries. */
public final class RegistryEntries {
    private RegistryEntries() {
    }

    public static @NotNull RegistryEntry<PlaceholderInfo> placeholder(@NotNull PlaceholderInfo info) {
        return new RegistryEntry<PlaceholderInfo>(
                info.getAddress(), info, info.getOwner(), info.getVisibility(), info.getDescription());
    }

    public static @NotNull RegistryEntry<FormatInfo> format(@NotNull FormatDef format) {
        String owner = format.getOwner().isEmpty() ? "pnLibrary" : format.getOwner();
        return new RegistryEntry<FormatInfo>(format.getName(), new FormatInfo(format.getName(), format.getInput().getSimpleName()), owner);
    }
}
