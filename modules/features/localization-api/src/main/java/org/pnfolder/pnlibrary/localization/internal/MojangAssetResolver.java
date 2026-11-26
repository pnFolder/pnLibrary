package org.pnfolder.pnlibrary.localization.internal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;
import org.pnfolder.pnlibrary.localization.TranslationException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Resolves Minecraft language files through the Mojang version manifest and asset indexes,
 * verifying every download before it is stored.
 */
final class MojangAssetResolver {
    /** Official Mojang version manifest. */
    static final URI MANIFEST = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");

    private static final int MANIFEST_LIMIT = 4 * 1024 * 1024;
    private static final int VERSION_LIMIT = 4 * 1024 * 1024;
    private static final int INDEX_LIMIT = 32 * 1024 * 1024;
    private static final int LANGUAGE_LIMIT = 16 * 1024 * 1024;
    private static final int CLIENT_LIMIT = 512 * 1024 * 1024;

    private final LocalizationHttpClient http;
    private final VerifiedFileStore store;
    /** Per-version asset metadata cache. */
    private final Map<MinecraftVersion, VersionAssets> versionAssets = new ConcurrentHashMap<MinecraftVersion, VersionAssets>();

    MojangAssetResolver(LocalizationHttpClient http, VerifiedFileStore store) {
        this.http = http;
        this.store = store;
    }

    /** Downloads and validates the current Mojang version manifest. */
    byte[] manifestBytes() throws IOException {
        byte[] bytes = http.get(MANIFEST, MANIFEST_LIMIT);
        validateManifest(bytes);
        return bytes;
    }

    /** Throws {@link TranslationException} when {@code bytes} is not a usable version manifest. */
    void validateManifest(byte[] bytes) {
        manifestEntries(bytes);
    }

    /** Lists release versions of the manifest that this library knows, newest first. */
    List<MinecraftVersion> versions(byte[] manifest) {
        Set<MinecraftVersion> known = new LinkedHashSet<MinecraftVersion>();
        for (JsonObject entry : manifestEntries(manifest)) {
            if (!"release".equals(string(entry, "type"))) {
                continue;
            }
            MinecraftVersion version = MinecraftVersion.parse(string(entry, "id"));
            if (version.isKnown()) {
                known.add(version);
            }
        }
        List<MinecraftVersion> sorted = new ArrayList<MinecraftVersion>(known);
        Collections.sort(sorted, new Comparator<MinecraftVersion>() {
            @Override
            public int compare(MinecraftVersion a, MinecraftVersion b) {
                if (a.getMajor() != b.getMajor()) {
                    return Integer.compare(b.getMajor(), a.getMajor());
                }
                if (a.getMinor() != b.getMinor()) {
                    return Integer.compare(b.getMinor(), a.getMinor());
                }
                return Integer.compare(b.getPatch(), a.getPatch());
            }
        });
        return sorted;
    }

    /** Lists locale ids published for {@code version}; {@code en_us} is always included because it ships in the client. */
    List<String> locales(MinecraftVersion version, byte[] manifest) throws IOException {
        Set<String> locales = new TreeSet<String>();
        for (String name : versionAssets(version, manifest).getObjects().keySet()) {
            if (name.startsWith("minecraft/lang/") && (name.endsWith(".json") || name.endsWith(".lang"))) {
                String file = name.substring(name.lastIndexOf('/') + 1);
                int dot = file.lastIndexOf('.');
                locales.add(dot < 0 ? file : file.substring(0, dot));
            }
        }
        locales.add("en_us");
        return new ArrayList<String>(locales);
    }

    /** Downloads the language file of {@code locale}, verifying size and SHA-1; falls back to the client jar when not in the index. */
    ResolvedLanguage language(MinecraftVersion version, String locale, byte[] manifest) throws IOException {
        VersionAssets assets = versionAssets(version, manifest);
        AssetReference reference = assets.getObjects().get("minecraft/lang/" + locale + ".json");
        if (reference == null) {
            reference = assets.getObjects().get("minecraft/lang/" + locale + ".lang");
        }
        if (reference == null) {
            return languageFromClient(version, locale, assets.getMetadata());
        }
        String hash = reference.getHash();
        URI uri = URI.create("https://resources.download.minecraft.net/" + hash.substring(0, Math.min(2, hash.length())) + "/" + hash);
        byte[] bytes = http.get(uri, LANGUAGE_LIMIT);
        if (bytes.length != reference.getSize() || !store.sha1(bytes).equals(hash)) {
            throw new TranslationException(TranslationException.Reason.INTEGRITY,
                    "Checksum or size mismatch for Minecraft " + version + " locale " + locale);
        }
        return new ResolvedLanguage(bytes, hash);
    }

    /** Version assets, resolved once per version and kept in memory. */
    private VersionAssets versionAssets(final MinecraftVersion version, final byte[] manifest) throws IOException {
        try {
            return versionAssets.computeIfAbsent(version, key -> {
                try {
                    return resolveVersionAssets(key, manifest);
                } catch (IOException error) {
                    throw new UncheckedIOException(error);
                }
            });
        } catch (UncheckedIOException error) {
            throw error.getCause();
        }
    }

    /** Loads version metadata and the asset index, from the disk cache or the network. */
    private VersionAssets resolveVersionAssets(MinecraftVersion version, byte[] manifest) throws IOException {
        JsonObject versionEntry = null;
        for (JsonObject entry : manifestEntries(manifest)) {
            if (version.getText().equals(string(entry, "id"))) {
                versionEntry = entry;
                break;
            }
        }
        if (versionEntry == null) {
            throw new TranslationException(TranslationException.Reason.UNSUPPORTED_VERSION,
                    "Minecraft " + version.getText() + " is absent from the Mojang manifest");
        }
        String versionUrl = string(versionEntry, "url");
        if (versionUrl == null) {
            throw malformed("Version manifest entry has no URL");
        }
        JsonObject versionJson = cachedJson(
                store.versionMetadata(version.getText()), URI.create(versionUrl), VERSION_LIMIT, "version metadata",
                string(versionEntry, "sha1"), null, this::validateVersionMetadata);
        JsonObject indexDescriptor = versionJson.getAsJsonObject("assetIndex");
        String indexUrl = string(indexDescriptor, "url");
        if (indexUrl == null) {
            throw malformed("Version metadata has no asset index URL");
        }
        JsonElement size = indexDescriptor.get("size");
        JsonObject index = cachedJson(
                store.assetIndex(version.getText()), URI.create(indexUrl), INDEX_LIMIT, "asset index",
                string(indexDescriptor, "sha1"), size == null || size.isJsonNull() ? null : Long.valueOf(size.getAsLong()),
                this::validateAssetIndex);
        return new VersionAssets(versionJson, assetObjects(index));
    }

    private Map<String, AssetReference> assetObjects(JsonObject index) {
        JsonObject objects = index.getAsJsonObject("objects");
        if (objects == null) {
            throw malformed("Asset index has no objects");
        }
        Map<String, AssetReference> result = new LinkedHashMap<String, AssetReference>();
        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            JsonObject value = entry.getValue().getAsJsonObject();
            String hash = string(value, "hash");
            if (hash == null) {
                throw malformed("Asset " + entry.getKey() + " has no hash");
            }
            JsonElement size = value.get("size");
            if (size == null) {
                throw malformed("Asset " + entry.getKey() + " has no size");
            }
            result.put(entry.getKey(), new AssetReference(hash, size.getAsInt()));
        }
        return result;
    }

    /** Returns verified JSON from the disk cache, or downloads and caches it; a corrupt cache file is quarantined. */
    private JsonObject cachedJson(Path path, URI uri, int limit, String name, String expectedHash, Long expectedSize,
                                  Consumer<JsonObject> validator) throws IOException {
        byte[] cached = store.read(path, limit);
        if (cached != null) {
            try {
                return decodeJson(cached, name, expectedHash, expectedSize, validator);
            } catch (TranslationException error) {
                store.quarantine(path);
            }
        }
        byte[] bytes = http.get(uri, limit);
        JsonObject parsed = decodeJson(bytes, name, expectedHash, expectedSize, validator);
        store.write(path, bytes);
        return parsed;
    }

    /** Checks size and SHA-1 when known, parses {@code bytes} and runs {@code validator}; failures become {@link TranslationException}. */
    private JsonObject decodeJson(byte[] bytes, String name, String expectedHash, Long expectedSize,
                                  Consumer<JsonObject> validator) {
        if ((expectedSize != null && bytes.length != expectedSize.longValue())
                || (expectedHash != null && !store.sha1(bytes).equals(expectedHash))) {
            throw new TranslationException(
                    TranslationException.Reason.INTEGRITY, "Checksum or size mismatch for Minecraft " + name);
        }
        JsonObject parsed = parse(bytes, name);
        try {
            validator.accept(parsed);
        } catch (TranslationException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new TranslationException(TranslationException.Reason.MALFORMED_DATA, "Malformed Minecraft " + name, error);
        }
        return parsed;
    }

    /** Requires an asset index with a URL. */
    private void validateVersionMetadata(JsonObject metadata) {
        JsonObject index = metadata.getAsJsonObject("assetIndex");
        if (index == null) {
            throw malformed("Version metadata has no asset index");
        }
        if (isBlank(string(index, "url"))) {
            throw malformed("Version metadata has no asset index URL");
        }
    }

    /** Requires every asset to have a hash and a non-negative size. */
    private void validateAssetIndex(JsonObject index) {
        JsonObject objects = index.getAsJsonObject("objects");
        if (objects == null) {
            throw malformed("Asset index has no objects");
        }
        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            String key = entry.getKey();
            if (!entry.getValue().isJsonObject()) {
                throw malformed("Asset " + key + " is not an object");
            }
            JsonObject value = entry.getValue().getAsJsonObject();
            if (isBlank(string(value, "hash"))) {
                throw malformed("Asset " + key + " has no hash");
            }
            if (!value.has("size") || value.get("size").isJsonNull() || value.get("size").getAsLong() < 0) {
                throw malformed("Asset " + key + " has no valid size");
            }
        }
    }

    /** Fallback for locales absent from the asset index: downloads the client jar and extracts the language file. */
    private ResolvedLanguage languageFromClient(MinecraftVersion version, String locale, JsonObject metadata) {
        JsonObject downloads = metadata.getAsJsonObject("downloads");
        JsonObject client = downloads == null ? null : downloads.getAsJsonObject("client");
        if (client == null) {
            throw unavailable(version, locale);
        }
        String url = string(client, "url");
        if (url == null) {
            throw unavailable(version, locale);
        }
        String hash = string(client, "sha1");
        if (hash == null) {
            throw malformed("Client artifact has no SHA-1");
        }
        JsonElement sizeElement = client.get("size");
        if (sizeElement == null) {
            throw malformed("Client artifact has no size");
        }
        int size = sizeElement.getAsInt();
        Path temporary;
        try {
            temporary = store.temporary(".jar");
        } catch (Exception error) {
            throw new TranslationException(
                    TranslationException.Reason.OFFLINE, "Unable to create temporary Minecraft archive", error);
        }
        try {
            long downloaded = http.download(URI.create(url), CLIENT_LIMIT, temporary);
            if (downloaded != (long) size || !store.sha1(temporary).equals(hash)) {
                throw new TranslationException(TranslationException.Reason.INTEGRITY,
                        "Checksum or size mismatch for Minecraft " + version + " client artifact");
            }
            return extractLanguage(temporary, version, locale);
        } catch (TranslationException error) {
            throw error;
        } catch (Exception error) {
            throw new TranslationException(TranslationException.Reason.MALFORMED_DATA,
                    "Unable to read Minecraft " + version + " client archive", error);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (Exception error) {
                temporary.toFile().deleteOnExit();
            }
        }
    }

    /** Reads the language file of {@code locale} from the downloaded client archive. */
    private ResolvedLanguage extractLanguage(Path archive, MinecraftVersion version, String locale) throws IOException {
        List<String> names = Arrays.asList(
                "assets/minecraft/lang/" + locale + ".json", "assets/minecraft/lang/" + locale + ".lang");
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry entry = null;
            for (String name : names) {
                entry = zip.getEntry(name);
                if (entry != null) {
                    break;
                }
            }
            if (entry == null) {
                throw unavailable(version, locale);
            }
            if (entry.isDirectory() || entry.getSize() > LANGUAGE_LIMIT) {
                throw new TranslationException(TranslationException.Reason.INTEGRITY,
                        "Minecraft language entry exceeds " + LANGUAGE_LIMIT + " bytes");
            }
            byte[] bytes = readEntry(zip, entry);
            return new ResolvedLanguage(bytes, store.sha1(bytes));
        }
    }

    /** Reads a language file, stopping as soon as it grows past the limit whatever the archive claims. */
    private byte[] readEntry(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream input = zip.getInputStream(entry)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min((int) Math.max(entry.getSize(), 0L), 8192));
            byte[] buffer = new byte[8192];
            while (true) {
                int read = input.read(buffer);
                if (read < 0) {
                    break;
                }
                if (output.size() + read > LANGUAGE_LIMIT) {
                    throw new TranslationException(TranslationException.Reason.INTEGRITY,
                            "Minecraft language entry exceeds " + LANGUAGE_LIMIT + " bytes");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    /** Error for a version that does not publish {@code locale}. */
    private static TranslationException unavailable(MinecraftVersion version, String locale) {
        return new TranslationException(TranslationException.Reason.UNAVAILABLE_LOCALE,
                "Minecraft " + version.getText() + " does not publish locale " + locale);
    }

    /** Parses a JSON object, mapping any failure to MALFORMED_DATA. */
    private static JsonObject parse(byte[] bytes, String name) {
        try {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception error) {
            throw new TranslationException(TranslationException.Reason.MALFORMED_DATA, "Malformed Minecraft " + name, error);
        }
    }

    /** Parses and validates the {@code versions} array of the manifest. */
    private List<JsonObject> manifestEntries(byte[] bytes) {
        try {
            JsonArray entries = parse(bytes, "version manifest").getAsJsonArray("versions");
            if (entries == null) {
                throw malformed("Version manifest has no versions");
            }
            List<JsonObject> result = new ArrayList<JsonObject>();
            for (JsonElement entry : entries) {
                JsonObject value = entry.getAsJsonObject();
                if (isBlank(string(value, "id")) || isBlank(string(value, "type")) || isBlank(string(value, "url"))) {
                    throw malformed("Version manifest entry requires id, type and URL");
                }
                result.add(value);
            }
            return result;
        } catch (TranslationException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new TranslationException(
                    TranslationException.Reason.MALFORMED_DATA, "Malformed Minecraft version manifest", error);
        }
    }

    /** MALFORMED_DATA error with {@code message}. */
    private static TranslationException malformed(String message) {
        return new TranslationException(TranslationException.Reason.MALFORMED_DATA, message);
    }

    /** String value of {@code name}, or null when missing or JSON null. */
    private static String string(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static boolean isBlank(String value) {
        return value == null || Whitespace.strip(value).isEmpty();
    }
}
