package org.pnfolder.pnlibrary.bukkit.item;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.inventory.meta.SkullMeta;

/** Older servers: an authlib GameProfile written into the private {@code profile} field of the skull. */
final class LegacyProfileSetter implements ProfileSetter {
    private final Constructor<?> newProfile;
    private final Method getProperties;
    private final Method putProperty;
    private final Constructor<?> newProperty;
    private final ConcurrentHashMap<Class<?>, Field> fields = new ConcurrentHashMap<>();

    private LegacyProfileSetter(Constructor<?> newProfile, Method getProperties, Method putProperty,
                                Constructor<?> newProperty) {
        this.newProfile = newProfile;
        this.getProperties = getProperties;
        this.putProperty = putProperty;
        this.newProperty = newProperty;
    }

    @Override
    public void apply(SkullMeta meta, URL url) throws ReflectiveOperationException {
        UUID id = ProfileNames.id(url);
        Object profile = newProfile.newInstance(id, ProfileNames.name(id));
        String payload = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        String encoded = Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        putProperty.invoke(getProperties.invoke(profile), "textures", newProperty.newInstance("textures", encoded));
        profileField(meta.getClass()).set(meta, profile);
    }

    private Field profileField(Class<?> type) throws NoSuchFieldException {
        Field known = fields.get(type);
        if (known != null) {
            return known;
        }
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField("profile");
                field.setAccessible(true);
                fields.put(type, field);
                return field;
            } catch (NoSuchFieldException error) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException("profile");
    }

    /** The setter, or null when this server has no authlib. */
    static LegacyProfileSetter resolve(Reflection reflection) {
        try {
            Class<?> profileType = reflection.type("com.mojang.authlib.GameProfile");
            Class<?> propertyType = reflection.type("com.mojang.authlib.properties.Property");
            Method getProperties = reflection.method(profileType, "getProperties");
            return new LegacyProfileSetter(
                profileType.getConstructor(UUID.class, String.class),
                getProperties,
                reflection.method(getProperties.getReturnType(), "put", Object.class, Object.class),
                propertyType.getConstructor(String.class, String.class));
        } catch (ReflectiveOperationException | LinkageError error) {
            return null;
        }
    }
}
