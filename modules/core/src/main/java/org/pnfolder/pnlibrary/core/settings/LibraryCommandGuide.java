package org.pnfolder.pnlibrary.core.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * The one list of every {@code /pn} command and every permission of the library: the text of {@code Команды.txt} is
 * made from it and a test keeps it equal to the permissions of {@code plugin.yml}.
 *
 * <p>Whoever adds a command or a permission adds it here; nothing else lists them.
 */
public final class LibraryCommandGuide {
    /** The permission that opens every command of the library. */
    public static final String ADMIN = "pnlibrary.admin";

    private static final List<GuideSection> SECTIONS = Collections.unmodifiableList(Arrays.asList(
        UpdatesGuide.section(),
        JournalGuide.section(),
        PluginsGuide.section(),
        SettingsGuide.section(),
        NetworkGuide.section(),
        MenuGuide.section()));

    private static final List<String> NOTES = Collections.unmodifiableList(Arrays.asList(
        "Консоль выполняет все команды без прав.",
        "«Право: A или pnlibrary.admin» значит, что подойдёт любое из двух. "
            + "Если в заметке сказано «нужно и право …», нужны оба.",
        "Подкоманды /pn updates … требуют ещё pnlibrary.updates.view или pnlibrary.admin, "
            + "подкоманды /pn update … требуют ещё pnlibrary.updates.download или pnlibrary.admin.",
        "На игровом сервере команда называется /pn (есть и /pnlib, /pnlibrary). "
            + "На прокси она называется /pnproxy (есть и /pnp), в этом файле она везде записана как /pn."));

    private static final List<GuideEntry> ENTRIES = entries();
    private static final Set<String> PERMISSIONS = permissions();
    private static final Set<String> PROXY_ONLY = proxyOnly();

    private LibraryCommandGuide() {
    }

    /** The sections of the guide in print order. */
    public static @NotNull List<GuideSection> getSections() {
        return SECTIONS;
    }

    /** How to read the list: the rules that hold for all entries. */
    public static @NotNull List<String> getNotes() {
        return NOTES;
    }

    /** Every entry of every section. */
    public static @NotNull List<GuideEntry> getEntries() {
        return ENTRIES;
    }

    /** Every permission node the guide names, patterns excluded, plus {@code pnlibrary.admin}. */
    public static @NotNull Set<String> getPermissions() {
        return PERMISSIONS;
    }

    /** The permissions that exist only on a proxy and so are not declared in the Bukkit plugin.yml. */
    public static @NotNull Set<String> getProxyOnly() {
        return PROXY_ONLY;
    }

    /** The lines of the guide between the heading and the signature of {@code Команды.txt}. */
    public static @NotNull List<String> lines() {
        List<String> lines = new ArrayList<String>();
        for (GuideSection section : SECTIONS) {
            lines.add("");
            lines.add(section.getTitle());
            lines.add(repeat('-', section.getTitle().length()));
            for (GuideEntry entry : section.getEntries()) {
                lines.add("");
                lines.add(entry.getUsage());
                lines.add("    Право: " + rights(entry));
                lines.add("    " + entry.getDescription());
                if (entry.getScope().getNote() != null) {
                    lines.add("    " + entry.getScope().getNote());
                }
                if (entry.getNote() != null) {
                    lines.add("    " + entry.getNote());
                }
            }
        }
        return lines;
    }

    private static String rights(GuideEntry entry) {
        String permission = entry.getPermission();
        if (permission == null) {
            return "не нужно";
        }
        if (permission.equals(ADMIN)) {
            return permission;
        }
        return entry.getOrAdmin() ? permission + " или " + ADMIN : permission;
    }

    private static List<GuideEntry> entries() {
        List<GuideEntry> entries = new ArrayList<GuideEntry>();
        for (GuideSection section : SECTIONS) {
            entries.addAll(section.getEntries());
        }
        return Collections.unmodifiableList(entries);
    }

    private static Set<String> permissions() {
        Set<String> permissions = new LinkedHashSet<String>();
        for (GuideEntry entry : ENTRIES) {
            if (!entry.isPattern() && entry.getPermission() != null) {
                permissions.add(entry.getPermission());
            }
        }
        permissions.add(ADMIN);
        return Collections.unmodifiableSet(permissions);
    }

    private static Set<String> proxyOnly() {
        Set<String> proxy = new LinkedHashSet<String>();
        Set<String> others = new HashSet<String>();
        for (GuideEntry entry : ENTRIES) {
            if (entry.getPermission() == null) {
                continue;
            }
            (entry.getScope() == GuideScope.PROXY ? proxy : others).add(entry.getPermission());
        }
        proxy.removeAll(others);
        proxy.remove(ADMIN);
        return Collections.unmodifiableSet(proxy);
    }

    private static String repeat(char symbol, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, symbol);
        return new String(chars);
    }
}
