package ru.privatenull.pnlibrary.update;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.privatenull.pnlibrary.banner.PluginBanner;

/** Получает и разбирает последний GitHub Release. */
final class GitHubReleaseClient {

    private static final Pattern TAG_FIELD = jsonStringField("tag_name");
    private static final Pattern URL_FIELD = jsonStringField("html_url");
    private static final Pattern DOWNLOAD_URL_FIELD = jsonStringField("browser_download_url");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final PluginBanner.Identity identity;

    GitHubReleaseClient(PluginBanner.Identity identity) {
        this.identity = identity;
    }

    LatestRelease fetchLatest() throws Exception {
        PluginBanner.GitHubRepository repository = identity.github();
        HttpRequest request = request(repository.apiUrl(), "application/vnd.github+json");
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) return null;
        requireSuccess(response.statusCode(), "GitHub API");

        String version = field(response.body(), TAG_FIELD);
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("GitHub не вернул tag_name");
        }

        String pageUrl = field(response.body(), URL_FIELD);
        String downloadUrl = matchingAssetUrl(response.body(), identity.updateAssetPattern());
        return new LatestRelease(
                version,
                pageUrl == null ? repository.releasesUrl() : pageUrl,
                downloadUrl
        );
    }

    HttpResponse<java.io.InputStream> download(String url) throws Exception {
        return HTTP_CLIENT.send(
                request(url, "application/java-archive, application/octet-stream"),
                HttpResponse.BodyHandlers.ofInputStream()
        );
    }

    private HttpRequest request(String url, String accept) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(identity.updateTimeout())
                .header("Accept", accept)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", identity.plugin().getName() + "-PnLibrary-Updater")
                .GET()
                .build();
    }

    static void requireSuccess(int statusCode, String source) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException(source + " вернул HTTP " + statusCode);
        }
    }

    private static String matchingAssetUrl(String json, Pattern assetPattern) {
        Matcher matcher = DOWNLOAD_URL_FIELD.matcher(json);
        while (matcher.find()) {
            String url = unescapeJsonString(matcher.group(1));
            String path = URI.create(url).getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            if (assetPattern.matcher(fileName).matches()) return url;
        }
        return null;
    }

    private static Pattern jsonStringField(String field) {
        return Pattern.compile("\\\"" + Pattern.quote(field)
                + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"");
    }

    private static String field(String json, Pattern pattern) {
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? unescapeJsonString(matcher.group(1)) : null;
    }

    private static String unescapeJsonString(String value) {
        return value.replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
