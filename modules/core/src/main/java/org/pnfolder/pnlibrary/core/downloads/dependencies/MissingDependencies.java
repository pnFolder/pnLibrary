package org.pnfolder.pnlibrary.core.downloads.dependencies;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration;

/** Missing dependencies waiting for the administrator, found by the code printed with them. */
public final class MissingDependencies {
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final int CODE_PARTS = 4;
    private static final int PART_LENGTH = 4;

    private final List<MissingGroup> groups = new ArrayList<>();
    private final SecureRandom random = new SecureRandom();

    /** Remembers the missing {@code items} of {@code owner} under a new code. */
    public synchronized @NotNull MissingGroup add(@NotNull String owner, @NotNull List<MissingDependency> items,
                                                  @Nullable DownloadRegistration registration) {
        MissingGroup group = new MissingGroup(owner, newCode(), items, registration);
        groups.add(group);
        return group;
    }

    /** The groups that are not installed yet. */
    public synchronized @NotNull List<MissingGroup> pending() {
        List<MissingGroup> pending = new ArrayList<>();
        for (MissingGroup group : groups) {
            if (group.getPending()) {
                pending.add(group);
            }
        }
        return pending;
    }

    /** The pending group with this {@code code}, ignoring the case, or null. */
    public synchronized @Nullable MissingGroup find(@NotNull String code) {
        String wanted = code.trim();
        for (MissingGroup group : groups) {
            if (group.getPending() && group.getCode().equalsIgnoreCase(wanted)) {
                return group;
            }
        }
        return null;
    }

    private String newCode() {
        StringBuilder code = new StringBuilder();
        for (int part = 0; part < CODE_PARTS; part++) {
            if (part > 0) {
                code.append('-');
            }
            for (int index = 0; index < PART_LENGTH; index++) {
                code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
        }
        return code.toString();
    }
}
