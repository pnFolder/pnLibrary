package org.pnfolder.pnlibrary.bukkit.localization.internal;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;
import org.pnfolder.pnlibrary.common.lang.Failures;

/** Name shapes and reflective reads that differ between old and new servers; kept out of the public API types. */
final class TranslationNames {
    private TranslationNames() {
    }

    /** Exact key via {@code org.bukkit.Translatable}, absent on legacy servers. */
    static String translationKey(Object value) {
        try {
            Method method = noArgMethod(value, "getTranslationKey");
            return method == null ? null : asString(method.invoke(value));
        } catch (ReflectiveOperationException error) {
            return null;
        }
    }

    /** Namespaced key (e.g. {@code minecraft:sharpness}) via {@code getKey().getKey()}, absent on legacy servers. */
    static String namespacedKey(Object value) {
        try {
            Method method = noArgMethod(value, "getKey");
            Object key = method == null ? null : method.invoke(value);
            if (key == null) {
                return null;
            }
            Method inner = noArgMethod(key, "getKey");
            Object text = inner == null ? null : inner.invoke(key);
            return text == null ? null : text.toString();
        } catch (ReflectiveOperationException error) {
            return null;
        }
    }

    /** Value of the public static field named {@code name} on {@code type}, or null. */
    static Object staticField(Class<?> type, String name) {
        try {
            for (java.lang.reflect.Field field : type.getFields()) {
                if (field.getName().equals(name)) {
                    return field.get(null);
                }
            }
            return null;
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(error);
        }
    }

    /** Legacy constant name (e.g. {@code DAMAGE_ALL}), read from the static field to avoid {@code getName()} on unbound wrappers. */
    @SuppressWarnings("deprecation")
    static String legacyName(Enchantment enchantment) {
        String field = staticFieldName(Enchantment.class, enchantment);
        if (field != null) {
            return field;
        }
        String name = Failures.runCatchingNonFatal(enchantment::getName).getOrNull();
        return name != null ? name : "unknown";
    }

    /** Legacy constant name (e.g. {@code FAST_DIGGING}), read from the static field to avoid {@code getName()} on unbound wrappers. */
    static String effectName(PotionEffectType effect) {
        String field = staticFieldName(PotionEffectType.class, effect);
        if (field != null) {
            return field;
        }
        String name = Failures.runCatchingNonFatal(effect::getName).getOrNull();
        return name != null ? name : "unknown";
    }

    /** {@code DIAMOND_SWORD} to {@code diamondSword}, the shape of a pre-1.13 tile/item key fragment. */
    static String camelCase(String name) {
        String[] words = name.toLowerCase(Locale.ROOT).split("_", -1);
        StringBuilder result = new StringBuilder(words[0]);
        for (int index = 1; index < words.length; index++) {
            result.append(capitalize(words[index]));
        }
        return result.toString();
    }

    /** {@code ender_dragon} to {@code EnderDragon}, the shape of a pre-1.13 entity key fragment. */
    static String pascalCase(String path) {
        StringBuilder result = new StringBuilder();
        for (String word : path.toLowerCase(Locale.ROOT).split("_", -1)) {
            result.append(capitalize(word));
        }
        return result.toString();
    }

    /** The text after the first colon, or the whole text when there is none. */
    static String afterColon(String text) {
        return text.substring(text.indexOf(':') + 1);
    }

    /** Lower-case constant name with spaces, the fallback shown for an untranslated value. */
    static String readable(String constantName) {
        return constantName.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /** Removes repeated entries, keeping the first of each. */
    static List<String> distinct(List<String> keys) {
        return Collections.unmodifiableList(new ArrayList<>(new LinkedHashSet<>(keys)));
    }

    private static String staticFieldName(Class<?> type, Object value) {
        try {
            for (java.lang.reflect.Field field : type.getFields()) {
                if (field.get(null) == value) {
                    return field.getName();
                }
            }
            return null;
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(error);
        }
    }

    private static Method noArgMethod(Object target, String name) {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == 0) {
                return method;
            }
        }
        return null;
    }

    private static String asString(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private static String capitalize(String word) {
        return word.isEmpty() ? word : word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
    }
}
