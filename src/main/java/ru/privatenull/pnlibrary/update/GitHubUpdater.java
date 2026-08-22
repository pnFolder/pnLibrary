package ru.privatenull.pnlibrary.update;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.privatenull.pnlibrary.text.ColorUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Complete GitHub release updater used by {@code PluginRuntime}. */
public final class GitHubUpdater implements AutoCloseable {

    private static final Pattern VERSION_FIELD = Pattern.compile(
            "\\\"(?:version|latestVersion|latest_version|tag_name|name)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DOWNLOAD_FIELD = Pattern.compile(
            "\\\"(?:browser_download_url|downloadUrl|download_url|html_url)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VERSION_VALUE = Pattern.compile(
            "v?\\d+(?:\\.\\d+){0,3}(?:[-+][A-Za-z0-9._-]+)?", Pattern.CASE_INSENSITIVE);

    private final JavaPlugin plugin;
    private final String repository;
    private final String notifyPermission;
    private final String supportUrl;
    private final String releasesUrl;
    private BukkitTask task;
    private volatile boolean completed;
    private volatile boolean available;
    private volatile String latestVersion;
    private volatile String downloadUrl;
    private volatile String lastError;

    public GitHubUpdater(
            JavaPlugin plugin,
            String repository,
            String notifyPermission,
            String supportUrl
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.repository = requireRepository(repository);
        this.notifyPermission = requireText(notifyPermission, "notifyPermission");
        this.supportUrl = supportUrl == null ? "" : supportUrl.trim();
        this.releasesUrl = "https://github.com/" + this.repository + "/releases/latest";
        this.downloadUrl = releasesUrl;
    }

    public void start() {
        restart();
    }

    public void restart() {
        cancel();
        completed = false;
        available = false;
        latestVersion = null;
        lastError = null;
        downloadUrl = releasesUrl;
        long period = Duration.ofHours(6).toSeconds() * 20L;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::check, 100L, period);
    }

    public void cancel() {
        if (task != null) task.cancel();
        task = null;
    }

    @Override
    public void close() {
        cancel();
    }

    public void notifyAdminOnJoin(Player player) {
        if (available && player != null && player.hasPermission(notifyPermission)) notifyPlayer(player);
    }

    public boolean isCheckCompleted() {
        return completed;
    }

    public boolean isUpdateAvailable() {
        return available;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getLastError() {
        return lastError;
    }

    private void check() {
        try {
            Release release = fetchLatest();
            completed = true;
            lastError = null;
            boolean firstNotice = !available || !release.version().equalsIgnoreCase(latestVersion);
            latestVersion = release.version();
            downloadUrl = release.downloadUrl();
            available = Version.parse(release.version()).compareTo(
                    Version.parse(plugin.getDescription().getVersion())) > 0;
            if (available && firstNotice) {
                plugin.getLogger().warning(System.lineSeparator() + consoleMessage(release));
                Bukkit.getScheduler().runTask(plugin, this::notifyOnlineAdmins);
            }
        } catch (Exception exception) {
            completed = true;
            lastError = exception.getMessage();
            plugin.getLogger().warning(pluginName() + " update check failed: " + lastError);
        }
    }

    private Release fetchLatest() throws Exception {
        Exception failure = null;
        for (String source : sources()) {
            try {
                String response = fetch(source);
                String version = cleanVersion(value(VERSION_FIELD, response));
                if (version == null) continue;
                String download = value(DOWNLOAD_FIELD, response);
                return new Release(version, download == null ? releasesUrl : unescape(download));
            } catch (Exception exception) {
                failure = exception;
            }
        }
        throw new IllegalStateException("All GitHub update sources are unavailable", failure);
    }

    private List<String> sources() {
        String raw = "https://raw.githubusercontent.com/" + repository + "/";
        String api = "https://api.github.com/repos/" + repository + "/";
        return List.of(
                raw + "main/update-manifest.json",
                raw + "master/update-manifest.json",
                api + "releases/latest",
                api + "tags"
        );
    }

    private String fetch(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", pluginName() + " pnLibrary updater");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IllegalStateException("GitHub HTTP " + status);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } finally {
            connection.disconnect();
        }
    }

    private void notifyOnlineAdmins() {
        for (Player player : Bukkit.getOnlinePlayers()) notifyAdminOnJoin(player);
    }

    private void notifyPlayer(Player player) {
        player.sendMessage(ColorUtil.component("&8&m                                                "));
        player.sendMessage(ColorUtil.component(
                "&#EFCC7F&lᴘ&#E2CC80&lɴ&#D5CB81&lꜰ&#C8CB82&lᴏ&#BBCB82&lʟ"
                        + "&#AECB83&lᴅ&#A1CA84&lᴇ&#94CA85&lʀ &8• &fОБНОВЛЕНИЯ"));
        player.sendMessage(Component.empty());
        player.sendMessage(ColorUtil.component("&fДля плагина &#D8DF9D&l" + pluginName()
                + " &fвышла новая версия."));
        player.sendMessage(ColorUtil.component("&7Вы можете скачать и установить её прямо сейчас."));
        player.sendMessage(Component.empty());
        player.sendMessage(ColorUtil.component("&fВерсия: &7" + currentVersion()
                + " &7→ &#9EFC65" + latestVersion));
        player.sendMessage(Component.empty());

        Component actions = link(
                "&#9EFC65&l[СКАЧАТЬ]",
                downloadUrl,
                "&fНажмите, чтобы открыть страницу загрузки");
        if (!supportUrl.isBlank()) {
            actions = actions
                    .append(ColorUtil.component("  "))
                    .append(link(
                            "&#5865F2&l[ПОДДЕРЖКА]",
                            supportUrl,
                            "&fНажмите, чтобы открыть Discord Plugin Folder"));
        }
        player.sendMessage(actions);
        player.sendMessage(ColorUtil.component("&8&m                                                "));
        player.sendTitle(
                ColorUtil.colorize("&#429F91&lPLUGIN FOLDER"),
                ColorUtil.colorize("&fОбновление &8• &#D8DF9D" + pluginName()
                        + " &7v" + latestVersion),
                10, 80, 20);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.45f, 1.6f);
    }

    private Component link(String text, String url, String hover) {
        return ColorUtil.component(text)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(ColorUtil.component(hover)));
    }

    private String consoleMessage(Release release) {
        return """
                ==================== %s Обновление ====================
                Доступна новая версия %s.
                Установлена: %s
                Новая:       %s
                Скачать:     %s
                Поддержка:   %s
                После замены JAR перезапустите сервер.
                ============================================================
                """.formatted(pluginName(), pluginName(), currentVersion(), release.version(),
                release.downloadUrl(), supportUrl.isBlank() ? "не указана" : supportUrl);
    }

    private String pluginName() {
        return plugin.getDescription().getName();
    }

    private String currentVersion() {
        return plugin.getDescription().getVersion();
    }

    private static String value(Pattern pattern, String response) {
        Matcher matcher = pattern.matcher(response);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String cleanVersion(String raw) {
        if (raw == null) return null;
        Matcher matcher = VERSION_VALUE.matcher(raw.trim());
        return matcher.find() ? matcher.group() : null;
    }

    private static String unescape(String value) {
        return value.replace("\\/", "/").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String requireRepository(String value) {
        String repository = requireText(value, "repository");
        if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid GitHub repository: " + repository);
        }
        return repository;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " cannot be blank");
        return value.trim();
    }

    private record Release(String version, String downloadUrl) {
    }

    private record Version(List<Integer> parts, String qualifier) implements Comparable<Version> {
        private static Version parse(String raw) {
            String value = raw == null ? "" : raw.trim();
            if (value.startsWith("v") || value.startsWith("V")) value = value.substring(1);
            String[] split = value.split("[-+]", 2);
            List<Integer> parts = new ArrayList<>();
            for (String part : split[0].split("\\.")) {
                try {
                    parts.add(Integer.parseInt(part.replaceAll("[^0-9].*$", "")));
                } catch (NumberFormatException ignored) {
                    parts.add(0);
                }
            }
            return new Version(parts, split.length > 1 ? split[1] : "");
        }

        @Override
        public int compareTo(Version other) {
            int size = Math.max(parts.size(), other.parts.size());
            for (int index = 0; index < size; index++) {
                int left = index < parts.size() ? parts.get(index) : 0;
                int right = index < other.parts.size() ? other.parts.get(index) : 0;
                int comparison = Integer.compare(left, right);
                if (comparison != 0) return comparison;
            }
            if (qualifier.isEmpty() && !other.qualifier.isEmpty()) return 1;
            if (!qualifier.isEmpty() && other.qualifier.isEmpty()) return -1;
            return qualifier.compareToIgnoreCase(other.qualifier);
        }
    }
}
