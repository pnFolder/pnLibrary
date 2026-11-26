package org.pnfolder.pnlibrary.core.placeholders;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.api.registry.Visibility;

/** The ways a placeholder can be written, for {@code /pn placeholders info} and the placeholder menu. */
public final class UsageVariants {
    private UsageVariants() {
    }

    /**
     * The ways {@code info} can be written, nearest first. Only the forms that can work for its visibility are listed:
     * a placeholder of {@code MODULE} is reachable by its own module alone, one of {@code PLUGIN} by the modules of its plugin.
     */
    public static @NotNull List<UsageVariant> of(@NotNull PlaceholderInfo info) {
        String address = info.getAddress();
        String owner = info.getOwner();
        int slash = owner.indexOf('/');
        String plugin = slash < 0 ? owner : owner.substring(0, slash);
        boolean shared = info.getVisibility() != Visibility.MODULE;
        List<UsageVariant> variants = new ArrayList<>();
        variants.add(new UsageVariant("Как есть", "{" + address + "}", "ближайший: свой модуль, свой плагин, потом остальные"));
        variants.add(new UsageVariant("Этот модуль", "{" + owner + "@" + address + "}", "именно модуль " + owner));
        if (shared) {
            variants.add(new UsageVariant("Весь плагин", "{" + plugin + "@" + address + "}", "любой модуль плагина " + plugin));
        }
        variants.add(new UsageVariant("Мой модуль", "{~~@" + address + "}", "только мой модуль: работает внутри " + owner));
        if (shared) {
            variants.add(new UsageVariant("Мой плагин", "{~@" + address + "}",
                    "только мой плагин: работает внутри любого модуля плагина " + plugin));
        }
        String format = info.getFormat() != null ? info.getFormat() : "имя";
        variants.add(new UsageVariant("С форматом", "{" + address + "|" + format + "}", "формат после чёрточки; список: /pn placeholders formats"));
        return variants;
    }

    /** The visibility of {@code info} as a lower case word. */
    static String scopeOf(PlaceholderInfo info) {
        return info.getVisibility().name().toLowerCase(Locale.ROOT);
    }
}
