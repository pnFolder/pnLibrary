package org.pnfolder.pnlibrary.core.plugin.module;

import java.util.Locale;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/** Derives the service owner key of a live module registration. */
public final class ModuleServiceKey {
    private ModuleServiceKey() {
    }

    public static @NotNull PluginId of(long registrationId, @NotNull PluginId nativePlugin, @NotNull ModuleId module) {
        // The same plugin and module IDs may appear again after a reload. The registration ID
        // keeps service ownership tied to one live plugin instance rather than its public name.
        String identity = nativePlugin.getValue() + "\u0000" + module.getValue() + "\u0000" + registrationId;
        String hash = sha256Hex(identity);
        return PluginId.of("m-" + take(nativePlugin.getValue(), 18) + "-" + take(module.getValue(), 18) + "-" + take(hash, 16));
    }

    private static String take(String text, int count) {
        return text.length() <= count ? text : text.substring(0, count);
    }

    private static String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
