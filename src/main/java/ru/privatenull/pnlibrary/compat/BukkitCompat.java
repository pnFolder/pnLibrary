package ru.privatenull.pnlibrary.compat;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.lang.reflect.Method;

/** Shared fallbacks for Bukkit APIs whose names or availability vary by server version. */
public final class BukkitCompat {

    private BukkitCompat() {
    }

    public static final class EnchantmentCompat {
        private EnchantmentCompat() {
        }

        @SuppressWarnings("deprecation")
        public static Enchantment unbreaking() {
            Enchantment enchantment = Enchantment.getByName("UNBREAKING");
            return enchantment != null ? enchantment : Enchantment.getByName("DURABILITY");
        }
    }

    public static final class MaterialCompat {
        private MaterialCompat() {
        }

        public static Material first(String... names) {
            if (names != null) {
                for (String name : names) {
                    if (name == null || name.isBlank()) continue;
                    Material material = Material.matchMaterial(name.trim());
                    if (material != null) return material;
                }
            }
            return Material.STONE;
        }
    }

    public static final class ParticleCompat {
        private ParticleCompat() {
        }

        public static Particle first(String... names) {
            if (names != null) {
                for (String name : names) {
                    Particle particle = byName(name);
                    if (particle != null) return particle;
                }
            }
            Particle fallback = byName("END_ROD");
            if (fallback != null) return fallback;
            Particle[] values = Particle.values();
            return values.length == 0 ? null : values[0];
        }

        public static void spawn(World world, Location location, String[] names, int count,
                                 double offsetX, double offsetY, double offsetZ, double extra) {
            spawn(world, first(names), location, count, offsetX, offsetY, offsetZ, extra);
        }

        public static void spawn(World world, Particle particle, Location location, int count,
                                 double offsetX, double offsetY, double offsetZ, double extra) {
            if (world == null || location == null || particle == null) return;
            try {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
            } catch (IllegalArgumentException ignored) {
                Particle fallback = first("END_ROD", "CLOUD");
                if (fallback == null || fallback == particle) return;
                try {
                    world.spawnParticle(fallback, location, count, offsetX, offsetY, offsetZ, extra);
                } catch (IllegalArgumentException ignoredAgain) {
                    // No compatible particle is available on this server.
                }
            }
        }

        public static void spawnBlock(World world, Location location, int count,
                                      double offsetX, double offsetY, double offsetZ, double extra,
                                      Material material) {
            if (world == null || location == null) return;
            Particle particle = first("BLOCK", "BLOCK_CRACK", "BLOCK_DUST");
            Material safeMaterial = material == null || material.isAir() ? Material.STONE : material;
            try {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra,
                        safeMaterial.createBlockData());
            } catch (Throwable ignored) {
                spawn(world, first("CLOUD", "END_ROD"), location, Math.max(1, count / 3),
                        offsetX, offsetY, offsetZ, extra);
            }
        }

        private static Particle byName(String name) {
            if (name == null || name.isBlank()) return null;
            try {
                return Particle.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    public static final class SoundCompat {
        private SoundCompat() {
        }

        public static void play(Player player, String[] names, float volume, float pitch) {
            if (player == null) return;
            Sound sound = first(names);
            if (sound != null) player.playSound(player.getLocation(), sound, volume, pitch);
        }

        public static void play(World world, Location location, String[] names, float volume, float pitch) {
            if (world == null || location == null) return;
            Sound sound = first(names);
            if (sound != null) world.playSound(location, sound, volume, pitch);
        }

        public static Sound first(String... names) {
            if (names != null) {
                for (String name : names) {
                    Sound sound = byName(name);
                    if (sound != null) return sound;
                }
            }
            return byName("UI_BUTTON_CLICK");
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static Sound byName(String name) {
            if (name == null || name.isBlank()) return null;
            try {
                Class<?> soundClass = Class.forName("org.bukkit.Sound", false,
                        SoundCompat.class.getClassLoader());
                Object value;
                if (soundClass.isEnum()) {
                    value = Enum.valueOf(soundClass.asSubclass(Enum.class),
                            name.trim().toUpperCase(java.util.Locale.ROOT));
                } else {
                    value = soundClass.getMethod("valueOf", String.class)
                            .invoke(null, name.trim().toUpperCase(java.util.Locale.ROOT));
                }
                return soundClass.isInstance(value) ? (Sound) value : null;
            } catch (ReflectiveOperationException | IllegalArgumentException | ClassCastException ignored) {
                return null;
            }
        }
    }

    public static final class InventoryViewCompat {
        private static final Method TOP_INVENTORY = findTopInventory();

        private InventoryViewCompat() {
        }

        public static Inventory topInventory(Player player) {
            if (player == null || TOP_INVENTORY == null) return null;
            Object view = player.getOpenInventory();
            if (view == null) return null;
            try {
                Object top = TOP_INVENTORY.invoke(view);
                return top instanceof Inventory inventory ? inventory : null;
            } catch (ReflectiveOperationException | IllegalArgumentException | SecurityException ignored) {
                return null;
            }
        }

        private static Method findTopInventory() {
            try {
                return Class.forName("org.bukkit.inventory.InventoryView")
                        .getMethod("getTopInventory");
            } catch (ReflectiveOperationException | SecurityException ignored) {
                return null;
            }
        }
    }
}
