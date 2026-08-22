package ru.privatenull.pnlibrary.item;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import ru.privatenull.pnlibrary.text.ColorUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Creates textured player heads from a Base64 value, texture URL, or texture hash. */
public final class HeadUtil {

    private static final Pattern TEXTURE_URL = Pattern.compile(
            "https?://textures\\.minecraft\\.net/texture/[A-Za-z0-9]+"
    );

    private HeadUtil() {
    }

    public static ItemStack create(String base64OrUrlOrHash, String displayName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) {
            return head;
        }

        meta.setDisplayName(ColorUtil.colorize(displayName));
        URL skinUrl = extractSkinUrl(normalizeTexture(base64OrUrlOrHash));
        if (skinUrl != null && !applyModernProfile(meta, skinUrl) && !applyLegacyProfile(meta, skinUrl)) {
            Bukkit.getLogger().log(Level.FINE, "pnLibrary could not apply skull texture: {0}", skinUrl);
        }

        head.setItemMeta(meta);
        return head;
    }

    /** Returns a supported Base64, textures.minecraft.net URL or texture hash, otherwise null. */
    public static String normalizeTexture(String input) {
        if (input == null || input.isBlank()) return null;
        String value = input.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("base64:") || lower.startsWith("base64-")) {
            value = value.substring(7).trim();
        }
        if (TEXTURE_URL.matcher(value).matches() || value.matches("[A-Fa-f0-9]{32,}")) return value;
        try {
            String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            return TEXTURE_URL.matcher(decoded).find() ? value : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** Uses the public profile API available on current Bukkit/Paper versions. */
    private static boolean applyModernProfile(SkullMeta meta, URL skinUrl) {
        try {
            UUID uuid = profileId(skinUrl);
            Class<?> playerProfileType = Class.forName("org.bukkit.profile.PlayerProfile");
            Class<?> playerTexturesType = Class.forName("org.bukkit.profile.PlayerTextures");
            Method createProfile = Bukkit.class.getMethod("createPlayerProfile", UUID.class, String.class);
            Object profile = createProfile.invoke(null, uuid, shortProfileName(uuid));
            Object textures = playerProfileType.getMethod("getTextures").invoke(profile);

            playerTexturesType.getMethod("setSkin", URL.class).invoke(textures, skinUrl);
            playerProfileType.getMethod("setTextures", playerTexturesType).invoke(profile, textures);
            SkullMeta.class.getMethod("setOwnerProfile", playerProfileType).invoke(meta, profile);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** Fallback for 1.16.5, where the public PlayerProfile API does not yet exist. */
    private static boolean applyLegacyProfile(SkullMeta meta, URL skinUrl) {
        try {
            UUID uuid = profileId(skinUrl);
            GameProfile profile = new GameProfile(uuid, shortProfileName(uuid));
            String textureJson = "{\"textures\":{\"SKIN\":{\"url\":\"" + skinUrl + "\"}}}";
            String encoded = Base64.getEncoder().encodeToString(textureJson.getBytes(StandardCharsets.UTF_8));
            profile.getProperties().put("textures", new Property("textures", encoded));

            Field profileField = findField(meta.getClass(), "profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
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

    private static String shortProfileName(UUID uuid) {
        return "pn" + uuid.toString().replace("-", "").substring(0, 14);
    }

    private static UUID profileId(URL skinUrl) {
        return UUID.nameUUIDFromBytes(
                ("pnLibrary:" + skinUrl.toExternalForm()).getBytes(StandardCharsets.UTF_8)
        );
    }

    private static URL extractSkinUrl(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        try {
            String value = input.trim();
            Matcher directUrl = TEXTURE_URL.matcher(value);
            if (directUrl.find()) {
                return new URL(directUrl.group());
            }

            if (value.matches("[A-Fa-f0-9]{32,}")) {
                return new URL("https://textures.minecraft.net/texture/" + value);
            }

            String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            Matcher encodedUrl = TEXTURE_URL.matcher(decoded);
            return encodedUrl.find() ? new URL(encodedUrl.group()) : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
