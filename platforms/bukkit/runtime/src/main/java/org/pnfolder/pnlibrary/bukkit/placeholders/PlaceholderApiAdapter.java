package org.pnfolder.pnlibrary.bukkit.placeholders;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter;
import org.pnfolder.pnlibrary.api.placeholders.reply.PublishedAnswers;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;

/**
 * Bridge to PlaceholderAPI. Each prefix the library publishes becomes one expansion: {@code %prefix_group_branch%}
 * asks the library for {@code group.branch}. Texts of the library may also contain {@code %tokens%} of other
 * expansions.
 */
public final class PlaceholderApiAdapter implements PlaceholderAdapter, AutoCloseable {
    /** The server thread never waits: a late value is not answered at all. */
    static final long MAIN_THREAD_WAIT_MILLIS = 0L;
    /** Other threads wait this long. */
    static final long OTHER_THREAD_WAIT_MILLIS = 1000L;

    private final Plugin plugin;
    private final ConcurrentHashMap<String, Expansion> expansions = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public PlaceholderApiAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    @NotNull
    @Override
    public String getId() {
        return "placeholderapi";
    }

    @Override
    public boolean isAvailable() {
        return !closed.get() && plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    @Nullable
    @Override
    public Object resolve(@Nullable UUID playerId, @NotNull String expression) {
        if (!isAvailable()) {
            return null;
        }
        String token = "%" + expression.replace('.', '_') + "%";
        String value = PlaceholderAPI.setPlaceholders(offline(playerId), token);
        return value.equals(token) ? null : value;
    }

    @NotNull
    @Override
    public String render(@Nullable UUID playerId, @NotNull String text) {
        if (!isAvailable()) {
            return text;
        }
        return PlaceholderAPI.setPlaceholders(offline(playerId), text);
    }

    @NotNull
    @Override
    public AutoCloseable publish(@NotNull String prefix, @NotNull PublishedAnswers answers) {
        if (!isAvailable()) {
            throw new IllegalStateException("PlaceholderAPI is not installed");
        }
        if (prefix.indexOf('_') >= 0) {
            throw new IllegalArgumentException("PlaceholderAPI prefix '" + prefix + "' must not contain '_'");
        }
        Expansion expansion = new Expansion(prefix, answers);
        if (expansions.putIfAbsent(prefix, expansion) != null) {
            throw new IllegalStateException("PlaceholderAPI prefix '" + prefix + "' is already published");
        }
        if (!expansion.register()) {
            throw new IllegalStateException("PlaceholderAPI did not accept the prefix '" + prefix + "'");
        }
        return () -> {
            if (expansions.remove(prefix, expansion)) {
                expansion.unregister();
            }
        };
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (Expansion expansion : expansions.values()) {
            expansion.unregister();
        }
        expansions.clear();
    }

    private OfflinePlayer offline(UUID playerId) {
        return playerId == null ? null : plugin.getServer().getOfflinePlayer(playerId);
    }

    private final class Expansion extends PlaceholderExpansion {
        private final String prefix;
        private final PublishedAnswers answers;

        Expansion(String prefix, PublishedAnswers answers) {
            this.prefix = prefix;
            this.answers = answers;
        }

        @Override
        public String getIdentifier() {
            return prefix;
        }

        @Override
        public String getAuthor() {
            String authors = String.join(",", plugin.getDescription().getAuthors());
            return authors.trim().isEmpty() ? PnLibraryIdentity.NAME : authors;
        }

        @Override
        public String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onRequest(OfflinePlayer player, String params) {
            long wait = plugin.getServer().isPrimaryThread() ? MAIN_THREAD_WAIT_MILLIS : OTHER_THREAD_WAIT_MILLIS;
            return answers.answer(player == null ? null : player.getUniqueId(), params, wait);
        }
    }
}
