package org.pnfolder.pnlibrary.bukkit.item;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/** Makes player heads, with a skin from a texture hash, URL or Base64 payload. */
public final class PlayerHeads {
    private static final String TEXTURES = "https://textures.minecraft.net/texture/";
    private static final int FINISHED_CAPACITY = 128;
    private static final Pattern TEXTURE_URL = Pattern.compile("https?://textures\\.minecraft\\.net/texture/[A-Za-z0-9]+");
    private static final Pattern TEXTURE_HASH = Pattern.compile("[A-Fa-f0-9]{32,}");

    // Callers get clones so nobody mutates a shared cached stack.
    private static final CopyingCache<ItemStack> FINISHED = new CopyingCache<>(FINISHED_CAPACITY, ItemStack::clone);

    private static volatile ProfileSetter modern;
    private static volatile ProfileSetter legacy;
    private static volatile boolean resolved;

    private PlayerHeads() {
    }

    public static ItemStack create(String texture, String displayName) {
        URL url = skinUrl(normalizeTexture(texture));
        ItemStack head = url == null ? null : textured(url);
        if (head == null) {
            head = playerHead();
        }
        if (displayName == null) {
            return head;
        }
        ItemMeta meta = head.getItemMeta();
        if (meta == null) {
            return head;
        }
        meta.setDisplayName(ItemText.color(displayName));
        head.setItemMeta(meta);
        return head;
    }

    public static ItemStack textured(String hash) {
        try {
            return textured(new URI(TEXTURES + hash).toURL());
        } catch (URISyntaxException | MalformedURLException error) {
            throw new IllegalArgumentException(error);
        }
    }

    private static ItemStack textured(URL url) {
        String key = url.toString();
        ItemStack cached = FINISHED.get(key);
        if (cached != null) {
            return cached;
        }
        ItemStack head = playerHead();
        if (!(head.getItemMeta() instanceof SkullMeta)) {
            return null;
        }
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (!applied(meta, url)) {
            return null;
        }
        head.setItemMeta(meta);
        FINISHED.set(key, head);
        return head;
    }

    // 1.18.1+ has the Bukkit profile API; older servers take an authlib GameProfile in a private field.
    private static boolean applied(SkullMeta meta, URL url) {
        resolveSetters();
        return tryProfile(modern, meta, url) || tryProfile(legacy, meta, url);
    }

    private static synchronized void resolveSetters() {
        if (!resolved) {
            modern = ModernProfileSetter.resolve(Reflection.Runtime.INSTANCE);
            legacy = LegacyProfileSetter.resolve(Reflection.Runtime.INSTANCE);
            resolved = true;
        }
    }

    // A failure means "try the other one": each server version supports exactly one of the two profile APIs.
    private static boolean tryProfile(ProfileSetter setter, SkullMeta meta, URL url) {
        if (setter == null) {
            return false;
        }
        try {
            setter.apply(meta, url);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            return false;
        }
    }

    static String normalizeTexture(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) {
            return null;
        }
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("base64:") || lower.startsWith("base64-")) {
            value = value.substring(7).trim();
        }
        if (TEXTURE_URL.matcher(value).matches() || TEXTURE_HASH.matcher(value).matches()) {
            return value;
        }
        try {
            return TEXTURE_URL.matcher(decodeTexture(value)).find() ? value : null;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static ItemStack playerHead() {
        Material head = Material.matchMaterial("PLAYER_HEAD");
        if (head != null) {
            return new ItemStack(head);
        }
        Material skull = Material.matchMaterial("SKULL_ITEM");
        return new ItemStack(skull != null ? skull : Material.STONE, 1, (short) 3);
    }

    private static URL skinUrl(String value) {
        if (value == null) {
            return null;
        }
        try {
            if (TEXTURE_URL.matcher(value).matches()) {
                return new URI(value).toURL();
            }
            if (TEXTURE_HASH.matcher(value).matches()) {
                return new URI(TEXTURES + value).toURL();
            }
            Matcher found = TEXTURE_URL.matcher(decodeTexture(value));
            return found.find() ? new URI(found.group()).toURL() : null;
        } catch (URISyntaxException | MalformedURLException error) {
            throw new IllegalArgumentException(error);
        }
    }

    private static String decodeTexture(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
