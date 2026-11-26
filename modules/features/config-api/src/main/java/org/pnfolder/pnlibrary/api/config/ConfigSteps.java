package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Numbered changes that bring a configuration file from one version to the next.
 *
 * <pre>{@code
 * final class ShopSteps extends ConfigSteps<ShopConfig> {
 *     ShopSteps() {
 *         version(2, changes -> changes.rename("pool-size", "db.pool.size"));
 *         version(3, changes -> {
 *             Value address = changes.get("redis-address");
 *             changes.put("redis.host", address.before(":"));
 *             changes.put("redis.port", address.after(":").toInt());
 *             changes.remove("redis-address");
 *         });
 *     }
 * }
 * }</pre>
 *
 * <p>Version numbers start at 2 and must be consecutive; version 1 is the state before any steps. Add new versions at
 * the end and never edit an existing one, because files store the number of the last version applied to them.
 */
public class ConfigSteps<T> {
    /** Key that stores the last applied version in a configuration file. */
    public static final String VERSION_KEY = "_config-version";

    /** Version of a file that has no steps applied, including files without the marker. */
    public static final int BASE_VERSION = 1;

    /** Steps for a configuration that has never changed format. */
    public static final ConfigSteps<Object> NONE = new ConfigSteps<Object>();

    private final List<Group> groups = new ArrayList<Group>();

    /** Version written by this code: the highest declared number, or 1 when there are no steps. */
    public int getLatestVersion() {
        int latest = BASE_VERSION;
        for (Group group : groups) {
            latest = Math.max(latest, group.number);
        }
        return latest;
    }

    /** Declares the changes that bring a file to version {@code number}. */
    protected void version(int number, @NotNull Consumer<ConfigChanges> changes) {
        groups.add(new Group(number, changes));
    }

    /**
     * Applies every declared version above {@code fromVersion} to {@code document}, in order, and stores the reached
     * version under {@link #VERSION_KEY}. Returns the applied version numbers.
     *
     * @throws IllegalStateException when the numbering is broken or a step fails
     */
    @NotNull
    public List<Integer> applyTo(@NotNull ConfigDocument document, int fromVersion) {
        List<Integer> applied = new ArrayList<Integer>();
        for (Group group : plan()) {
            if (group.number <= fromVersion) {
                continue;
            }
            try {
                group.changes.accept(new ConfigChanges(document));
            } catch (Exception error) {
                throw new IllegalStateException("Config version " + group.number + " failed: " + error.getMessage(), error);
            }
            document.set(VERSION_KEY, group.number);
            applied.add(group.number);
        }
        return applied;
    }

    private List<Group> plan() {
        List<Group> sorted = new ArrayList<Group>(groups);
        Collections.sort(sorted, new Comparator<Group>() {
            @Override
            public int compare(Group a, Group b) {
                return Integer.compare(a.number, b.number);
            }
        });
        for (int index = 0; index < sorted.size(); index++) {
            int expected = BASE_VERSION + 1 + index;
            if (sorted.get(index).number != expected) {
                throw new IllegalArgumentException("Config versions must be numbered 2, 3, 4... without gaps or repeats; found "
                        + sorted.get(index).number + " where " + expected + " was expected");
            }
        }
        return sorted;
    }

    private static final class Group {
        final int number;
        final Consumer<ConfigChanges> changes;

        Group(int number, Consumer<ConfigChanges> changes) {
            this.number = number;
            this.changes = changes;
        }
    }
}
