package org.pnfolder.pnlibrary.core.scenario;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.placeholders.UsageVariant;

/** The ways a definition can be written in a configuration or an expression. */
public final class DefinitionUsage {
    private DefinitionUsage() {
    }

    /**
     * How {@code info} is written in a configuration or an expression, nearest first. A definition of {@code module}
     * visibility is reachable by its own module only, one of {@code plugin} visibility by the modules of its plugin.
     */
    public static @NotNull List<UsageVariant> of(@NotNull ScenarioDefinitionInfo info) {
        String owner = info.getOwner();
        int slash = owner.indexOf('/');
        String plugin = slash < 0 ? owner : owner.substring(0, slash);
        boolean shared = !info.getVisibility().equals("module");
        List<UsageVariant> variants = new ArrayList<>();
        variants.add(new UsageVariant("Как есть", written(info, null), "ближайший: свой модуль, свой плагин, потом остальные"));
        variants.add(new UsageVariant("Этот модуль", written(info, owner), "именно модуль " + owner));
        if (shared) {
            variants.add(new UsageVariant("Весь плагин", written(info, plugin), "любой модуль плагина " + plugin));
        }
        variants.add(new UsageVariant("Мой модуль", written(info, "~~"), "только мой модуль: работает внутри " + owner));
        if (shared) {
            variants.add(new UsageVariant("Мой плагин", written(info, "~"),
                "только мой плагин: работает внутри любого модуля плагина " + plugin));
        }
        return variants;
    }

    private static String written(ScenarioDefinitionInfo info, String owner) {
        String prefix = owner == null ? "" : owner + "@";
        switch (info.getKind()) {
            case "action":
                return "{ type: call, name: '" + prefix + info.getName() + "' }";
            case "requirement":
                return "{ type: ref, name: '" + prefix + info.getName() + "' }";
            default:
                return prefix + info.getName() + "()";
        }
    }
}
