package org.pnfolder.pnlibrary.core.plugin.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.compat.MinecraftCompatibility;
import org.pnfolder.pnlibrary.api.compat.MinecraftSupport;
import org.pnfolder.pnlibrary.api.compat.declaration.NewerAction;
import org.pnfolder.pnlibrary.api.compat.declaration.UnsupportedAction;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersionInfo;

/**
 * The Minecraft version check of one module. The check itself never changes; only the administrator's "start anyway"
 * does, so it is read from the preferences each time ({@code scope} is {@code plugin:module}).
 */
public final class MinecraftGate {
    public static final String KEY = "mcforce|";

    private final String scope;
    private final ModulePreferences preferences;
    private final MinecraftSupport support;
    private final String declared;
    private final String server;
    private final String warning;
    private final String blockReason;
    private final UnsupportedAction unsupportedAction;

    private MinecraftGate(String scope, ModulePreferences preferences, MinecraftSupport support, String declared, String server,
                          String warning, String blockReason, UnsupportedAction unsupportedAction) {
        this.scope = scope;
        this.preferences = preferences;
        this.support = support;
        this.declared = declared;
        this.server = server;
        this.warning = warning;
        this.blockReason = blockReason;
        this.unsupportedAction = unsupportedAction;
    }

    /** A sentence for the log when the module starts but the version is doubtful, or {@code null}. */
    public @Nullable String getWarning() {
        return warning;
    }

    public @NotNull UnsupportedAction getUnsupportedAction() {
        return unsupportedAction;
    }

    public boolean isForced() {
        return !preferences.autoStart(KEY + scope);
    }

    /** Why the module may not start now, or {@code null}. */
    public @Nullable String reason() {
        return isForced() ? null : blockReason;
    }

    /** Whether registering must fail instead of switching the module off. */
    public boolean mustFail(boolean controllable) {
        return reason() != null && (unsupportedAction == UnsupportedAction.STOP_PLUGIN || !controllable);
    }

    public void setForced(boolean on) {
        preferences.setAutoStart(KEY + scope, !on);
    }

    public @NotNull MinecraftCompatibility compatibility() {
        String note = blockReason != null ? blockReason : warning;
        return new MinecraftCompatibility(support, declared, server, isForced(), reason() != null, note);
    }

    /** Checks {@code server} against the module's own rule, or the plugin's when the module declared none. */
    public static @NotNull MinecraftGate open(@NotNull String scope, @NotNull ModulePreferences preferences,
                                              @NotNull MinecraftSupportRule moduleRule, @NotNull MinecraftSupportRule pluginRule,
                                              @Nullable String server) {
        MinecraftSupportRule rule = moduleRule.isDeclared() ? moduleRule : pluginRule;
        UnsupportedAction unsupported = firstNonNull(moduleRule.getUnsupportedAction(), pluginRule.getUnsupportedAction(),
                UnsupportedAction.DISABLE_MODULE);
        NewerAction newer = firstNonNull(moduleRule.getNewerAction(), pluginRule.getNewerAction(), NewerAction.ALLOW_WITH_WARNING);
        MinecraftVersionInfo info = server == null ? null : MinecraftVersion.parseInfo(server);
        if (info != null && !info.isNumeric()) {
            info = null;
        }
        String serverText = info == null ? null : describe(info);
        MinecraftSupport support = rule.getVersions().check(info);
        String declared = rule.getDescription();
        String[] verdict = judge(support, newer, unsupported, serverText, declared);
        return new MinecraftGate(scope, preferences, support, declared, serverText != null ? serverText : server, verdict[0], verdict[1],
                unsupported);
    }

    /** The warning and the block reason, either of which may be {@code null}. */
    private static String[] judge(MinecraftSupport support, NewerAction newer, UnsupportedAction unsupported, String serverText,
                                  String declared) {
        String warning = null;
        String block = null;
        if (support == MinecraftSupport.NEWER_THAN_DECLARED) {
            if (newer == NewerAction.ALLOW_WITH_WARNING) {
                warning = "Сервер на Minecraft " + serverText + ", это новее проверенных версий (" + declared + "): возможны проблемы.";
            } else if (newer == NewerAction.BLOCK) {
                block = "Нужен Minecraft " + declared + ", на сервере " + serverText + ": он новее.";
            }
        } else if (support == MinecraftSupport.OLDER_THAN_DECLARED || support == MinecraftSupport.EXCLUDED) {
            block = "Нужен Minecraft " + declared + ", на сервере " + serverText + ".";
        }
        if (block != null && unsupported == UnsupportedAction.WARN_ONLY) {
            return new String[] {block, null};
        }
        return new String[] {warning, block};
    }

    private static String describe(MinecraftVersionInfo info) {
        String text = info.getMajor() + "." + info.getMinor();
        Integer patch = info.getPatch();
        return patch != null && patch != 0 ? text + "." + patch : text;
    }

    private static <T> T firstNonNull(T first, T second, T fallback) {
        return first != null ? first : second != null ? second : fallback;
    }
}
