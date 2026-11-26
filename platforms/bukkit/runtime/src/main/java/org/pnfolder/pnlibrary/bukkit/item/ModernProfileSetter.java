package org.pnfolder.pnlibrary.bukkit.item;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.SkullMeta;

/** Bukkit profile API (1.18.1+). Every class and method is looked up once, when the setter is made. */
final class ModernProfileSetter implements ProfileSetter {
    private final Method createProfile;
    private final Method getTextures;
    private final Method setSkin;
    private final Method setTextures;
    private final Method setOwnerProfile;

    private ModernProfileSetter(Method createProfile, Method getTextures, Method setSkin, Method setTextures,
                                Method setOwnerProfile) {
        this.createProfile = createProfile;
        this.getTextures = getTextures;
        this.setSkin = setSkin;
        this.setTextures = setTextures;
        this.setOwnerProfile = setOwnerProfile;
    }

    @Override
    public void apply(SkullMeta meta, URL url) throws ReflectiveOperationException {
        UUID id = ProfileNames.id(url);
        Object profile = createProfile.invoke(null, id, ProfileNames.name(id));
        Object textures = getTextures.invoke(profile);
        setSkin.invoke(textures, url);
        setTextures.invoke(profile, textures);
        setOwnerProfile.invoke(meta, profile);
    }

    /** The setter, or null when this server has no profile API. */
    static ModernProfileSetter resolve(Reflection reflection) {
        return resolve(reflection, Bukkit.class, SkullMeta.class);
    }

    /** The setter built on the given classes, or null when this server has no profile API. */
    static ModernProfileSetter resolve(Reflection reflection, Class<?> bukkit, Class<?> skull) {
        try {
            Class<?> profileType = reflection.type("org.bukkit.profile.PlayerProfile");
            Class<?> texturesType = reflection.type("org.bukkit.profile.PlayerTextures");
            return new ModernProfileSetter(
                reflection.method(bukkit, "createPlayerProfile", UUID.class, String.class),
                reflection.method(profileType, "getTextures"),
                reflection.method(texturesType, "setSkin", URL.class),
                reflection.method(profileType, "setTextures", texturesType),
                reflection.method(skull, "setOwnerProfile", profileType));
        } catch (ReflectiveOperationException | LinkageError error) {
            return null;
        }
    }
}
