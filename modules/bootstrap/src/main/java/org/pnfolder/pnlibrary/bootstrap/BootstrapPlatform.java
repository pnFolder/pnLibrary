package org.pnfolder.pnlibrary.bootstrap;

import java.util.regex.Pattern;

/**
 * Server platform the installer works for: how to recognise pnLibrary in the downloaded jar.
 *
 * Every platform downloads the same {@code pnLibrary-<version>-universal.jar}: it carries the descriptors of all three.
 */
public enum BootstrapPlatform {
    /** Bukkit, Spigot, Paper and Folia: plugin {@code pnLibrary} in {@code plugin.yml}. */
    BUKKIT("Bukkit", "plugin.yml", BootstrapDefaults.PRODUCT_NAME, "/pn update " + BootstrapDefaults.PRODUCT_NAME, "stop"),

    /** BungeeCord and Waterfall: plugin {@code pnLibrary} in {@code bungee.yml}. */
    BUNGEECORD("BungeeCord", "bungee.yml", BootstrapDefaults.PRODUCT_NAME,"/pn update check; /pn update stage", "end"),

    /** Velocity: plugin id {@code pnlibrary} in {@code velocity-plugin.json}. */
    VELOCITY("Velocity", "velocity-plugin.json", BootstrapDefaults.PRODUCT_ID,"/pn update check; /pn update stage", "shutdown");

    private static final Pattern UNIVERSAL = Pattern.compile(
            "(?i)^" + Pattern.quote(BootstrapDefaults.PRODUCT_NAME) + "-[0-9][^/\\\\]*-universal\\.jar$");

    private final String displayName;
    private final String descriptor;
    private final String pluginName;
    private final String updateHint;
    private final String stopCommand;

    BootstrapPlatform(String displayName, String descriptor, String pluginName, String updateHint, String stopCommand) {
        this.displayName = displayName;
        this.descriptor = descriptor;
        this.pluginName = pluginName;
        this.updateHint = updateHint;
        this.stopCommand = stopCommand;
    }

    /** Returns the console command that stops this platform.
     * @return {@code stop}, {@code end} or {@code shutdown} */
    public String stopCommand() { return stopCommand; }

    /** Returns how an administrator updates pnLibrary on this platform.
     * @return a command to mention in messages */
    public String updateHint() { return updateHint; }

    /** Returns the platform name for messages.
     * @return for example {@code Velocity} */
    public String displayName() { return displayName; }

    /** Returns the name (Bukkit, BungeeCord) or id (Velocity) under which pnLibrary is registered.
     * @return the name to look pnLibrary up by in the platform's plugin manager */
    public String pluginName() { return pluginName; }

    /** Returns the descriptor file inside the plugin jar.
     * @return {@code plugin.yml}, {@code bungee.yml} or {@code velocity-plugin.json} */
    public String descriptor() { return descriptor; }

    static boolean isUniversal(String fileName) { return fileName != null && UNIVERSAL.matcher(fileName).matches(); }
}
