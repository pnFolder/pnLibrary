package ru.privatenull.pnlibrary.item;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Creates textured player heads from a texture hash, URL, or Base64 texture payload. */
public final class HeadUtil {
    private static final Pattern TEXTURE_URL = Pattern.compile(
            "https?://textures\\.minecraft\\.net/texture/[A-Za-z0-9]+"
    );

    private HeadUtil() {
    }

    /**
     * Creates one textured player head.
     *
     * @param texture texture hash, textures.minecraft.net URL, or Base64 payload
     * @param displayName optional legacy-formatted display name
     * @return a new player-head item; invalid textures produce an untextured head
     */
    public static ItemStack create(String texture, String displayName) {
        ItemStack head = playerHead();
        ItemMeta rawMeta = head.getItemMeta();
        if (!(rawMeta instanceof SkullMeta)) return head;
        SkullMeta meta = (SkullMeta) rawMeta;
        if (displayName != null) meta.setDisplayName(ItemText.color(displayName));

        URL skinUrl = extractSkinUrl(normalizeTexture(texture));
        if (skinUrl != null && !applyModernProfile(meta, skinUrl)) applyLegacyProfile(meta, skinUrl);
        head.setItemMeta(meta);
        return head;
    }

    /**
     * Validates and normalizes a supported texture representation.
     *
     * @param input texture hash, URL, prefixed value, or Base64 payload
     * @return normalized representation without a {@code base64:} prefix, or {@code null}
     */
    public static String normalizeTexture(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        String value = input.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("base64:") || lower.startsWith("base64-")) value = value.substring(7).trim();
        if (TEXTURE_URL.matcher(value).matches() || value.matches("[A-Fa-f0-9]{32,}")) return value;
        try {
            String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            return TEXTURE_URL.matcher(decoded).find() ? value : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static ItemStack playerHead() {
        Material modern = Material.matchMaterial("PLAYER_HEAD");
        if (modern != null) return new ItemStack(modern);
        Material legacy = Material.matchMaterial("SKULL_ITEM");
        return new ItemStack(legacy == null ? Material.STONE : legacy, 1, (short) 3);
    }

    private static boolean applyModernProfile(SkullMeta meta, URL skinUrl) {
        try {
            UUID id = profileId(skinUrl);
            Class<?> profileType = Class.forName("org.bukkit.profile.PlayerProfile");
            Class<?> texturesType = Class.forName("org.bukkit.profile.PlayerTextures");
            Method create = Bukkit.class.getMethod("createPlayerProfile", UUID.class, String.class);
            Object profile = create.invoke(null, id, shortProfileName(id));
            Object textures = profileType.getMethod("getTextures").invoke(profile);
            texturesType.getMethod("setSkin", URL.class).invoke(textures, skinUrl);
            profileType.getMethod("setTextures", texturesType).invoke(profile, textures);
            SkullMeta.class.getMethod("setOwnerProfile", profileType).invoke(meta, profile);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean applyLegacyProfile(SkullMeta meta, URL skinUrl) {
        try {
            UUID id = profileId(skinUrl);
            Class<?> profileType = Class.forName("com.mojang.authlib.GameProfile");
            Constructor<?> constructor = profileType.getConstructor(UUID.class, String.class);
            Object profile = constructor.newInstance(id, shortProfileName(id));
            Object properties = profileType.getMethod("getProperties").invoke(profile);
            Class<?> propertyType = Class.forName("com.mojang.authlib.properties.Property");
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + skinUrl + "\"}}}";
            String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            Object property = propertyType.getConstructor(String.class, String.class)
                    .newInstance("textures", encoded);
            properties.getClass().getMethod("put", Object.class, Object.class)
                    .invoke(properties, "textures", property);
            Field field = findField(meta.getClass(), "profile");
            field.setAccessible(true);
            field.set(meta, profile);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static URL extractSkinUrl(String input) {
        if (input == null) return null;
        try {
            Matcher direct = TEXTURE_URL.matcher(input);
            if (direct.find()) return new URL(direct.group());
            if (input.matches("[A-Fa-f0-9]{32,}")) {
                return new URL("https://textures.minecraft.net/texture/" + input);
            }
            String decoded = new String(Base64.getDecoder().decode(input), StandardCharsets.UTF_8);
            Matcher encoded = TEXTURE_URL.matcher(decoded);
            return encoded.find() ? new URL(encoded.group()) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static UUID profileId(URL url) {
        return UUID.nameUUIDFromBytes(("pnLibrary:" + url).getBytes(StandardCharsets.UTF_8));
    }

    private static String shortProfileName(UUID id) {
        return "pn" + id.toString().replace("-", "").substring(0, 14);
    }

}
