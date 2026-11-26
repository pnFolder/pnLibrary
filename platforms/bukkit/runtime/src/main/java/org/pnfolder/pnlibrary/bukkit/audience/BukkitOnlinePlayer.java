package org.pnfolder.pnlibrary.bukkit.audience;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.translation.Translator;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;
import org.pnfolder.pnlibrary.api.audience.delivery.Delivery;
import org.pnfolder.pnlibrary.api.world.ItemMatcher;
import org.pnfolder.pnlibrary.api.world.Particle;
import org.pnfolder.pnlibrary.api.world.Position;
import org.pnfolder.pnlibrary.api.world.StatusEffect;
import org.pnfolder.pnlibrary.common.lang.Failures;

/**
 * Version-safe {@link OnlinePlayer} backed by a Bukkit {@link Player}.
 *
 * <p>Chat, action-bar, and sound delivery is delegated to {@link BukkitAdventureTransport}, which can fall back
 * when native Adventure integration is unavailable. Effects and particles use Bukkit APIs with reflective
 * compatibility for older server versions.
 */
final class BukkitOnlinePlayer implements OnlinePlayer {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final long MILLIS_PER_TICK = 50L;
    private static final Method LOCALE_READER = localeReader();

    private final Player player;
    private final BukkitAdventureTransport audiences;

    BukkitOnlinePlayer(Player player, BukkitAdventureTransport audiences) {
        this.player = player;
        this.audiences = audiences;
    }

    @NotNull
    @Override
    public UUID getUniqueId() {
        return player.getUniqueId();
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public Pointers pointers() {
        return AudienceExtensions.playerPointers(player::getName, player.getUniqueId(),
            () -> LEGACY.deserialize(player.getDisplayName()), this::clientLocale, player::hasPermission);
    }

    // Player.getLocale exists since 1.12 and the runtime is built against 1.8, so it is looked up once by name.
    private Locale clientLocale() {
        if (LOCALE_READER == null) {
            return null;
        }
        String tag = Failures.runCatchingNonFatal(() -> {
            Object value = LOCALE_READER.invoke(player);
            return value instanceof String ? (String) value : null;
        }).getOrNull();
        return tag == null ? null : Translator.parseLocale(tag);
    }

    @Override
    public void sendMessage(@NotNull Component message) {
        audiences.sendMessage(player, message);
    }

    @Override
    public void sendActionBar(@NotNull Component message) {
        audiences.sendActionBar(player, message);
    }

    @Override
    public void playSound(@NotNull Sound sound) {
        audiences.playSound(player, sound);
    }

    @Override
    public void showTitle(@NotNull Title title) {
        audiences.showTitle(player, title);
    }

    @NotNull
    @Override
    public Delivery addEffect(@NotNull StatusEffect effect) {
        PotionEffectType type = PotionEffectType.getByName(effect.getKey().value().toUpperCase(Locale.ROOT));
        if (type == null) {
            return Delivery.unsupported();
        }
        long millis = effect.getDuration().toMillis() / MILLIS_PER_TICK;
        int ticks = (int) Math.min(Math.max(millis, 1L), Integer.MAX_VALUE);
        PotionEffect configured;
        try {
            Constructor<PotionEffect> constructor = PotionEffect.class.getConstructor(
                PotionEffectType.class, Integer.TYPE, Integer.TYPE, Boolean.TYPE, Boolean.TYPE, Boolean.TYPE);
            configured = constructor.newInstance(type, ticks, effect.getAmplifier(), effect.isAmbient(),
                effect.isParticles(), effect.isIcon());
        } catch (ReflectiveOperationException | RuntimeException error) {
            configured = new PotionEffect(type, ticks, effect.getAmplifier(), effect.isAmbient());
        }
        return Delivery.of(player.addPotionEffect(configured, true));
    }

    @NotNull
    @Override
    public Delivery runCommand(@NotNull String command) {
        return Delivery.of(player.getServer().dispatchCommand(player, command));
    }

    @NotNull
    @Override
    public Delivery runConsoleCommand(@NotNull String command) {
        return Delivery.of(player.getServer().dispatchCommand(player.getServer().getConsoleSender(), command));
    }

    /** Counts items in the main inventory and hotbar that fit {@code matcher}; unknown materials count as zero. */
    @Nullable
    @Override
    public Integer countItems(@NotNull ItemMatcher matcher) {
        Material material = materialOf(matcher.getMaterial().value());
        if (material == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material && fits(stack, matcher)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    @Nullable
    @Override
    public Double experienceLevel() {
        return (double) player.getLevel();
    }

    @Nullable
    @Override
    public Double experiencePoints() {
        return (double) player.getTotalExperience();
    }

    @Nullable
    @Override
    public Position position() {
        Location here = player.getLocation();
        String world = here.getWorld() == null ? "" : here.getWorld().getName();
        return new Position(world, here.getX(), here.getY(), here.getZ());
    }

    /** Renders a supported particle, returning an unsupported delivery when the server lacks the required API. */
    @NotNull
    @Override
    public Delivery spawnParticle(@NotNull Particle particle) {
        Class<?> type = Failures.runCatchingNonFatal(() -> Class.forName("org.bukkit.Particle")).getOrNull();
        if (type == null) {
            return Delivery.unsupported();
        }
        Object constant = particleConstant(type, particle.getKey().value());
        Method method = spawnMethod(type);
        if (constant == null || method == null) {
            return Delivery.unsupported();
        }
        return Failures.runCatchingNonFatal(() -> method.invoke(player, constant, player.getLocation(),
            particle.getCount(), particle.getOffsetX(), particle.getOffsetY(), particle.getOffsetZ(),
            particle.getSpeed())).fold(ignored -> Delivery.delivered(), error -> {
                Failures.rethrowIfFatal(error);
                return Delivery.failed(error);
            });
    }

    private Method spawnMethod(Class<?> type) {
        for (Method candidate : player.getClass().getMethods()) {
            if (candidate.getName().equals("spawnParticle") && candidate.getParameterTypes().length == 7
                && candidate.getParameterTypes()[0] == type) {
                return candidate;
            }
        }
        return null;
    }

    private static Object particleConstant(Class<?> type, String name) {
        for (Object constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(name)) {
                return constant;
            }
        }
        return null;
    }

    private static Material materialOf(String name) {
        String trimmed = name.trim();
        String bare = trimmed.startsWith("minecraft:") ? trimmed.substring(10) : trimmed;
        return Material.matchMaterial(bare.toUpperCase(Locale.ROOT));
    }

    @SuppressWarnings("deprecation")
    private static boolean fits(ItemStack stack, ItemMatcher query) {
        if (query.getName() == null && query.getNameContains() == null && query.getLoreContains() == null) {
            return true;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        boolean ignoreCase = query.isIgnoreCase();
        String display = meta.hasDisplayName() ? meta.getDisplayName() : null;
        String exactName = query.getName();
        if (exactName != null && !(display != null && equal(display, colored(exactName), ignoreCase))) {
            return false;
        }
        String partialName = query.getNameContains();
        if (partialName != null && !(display != null && contains(display, colored(partialName), ignoreCase))) {
            return false;
        }
        String needle = query.getLoreContains();
        return needle == null || loreContains(meta.getLore(), colored(needle), ignoreCase);
    }

    private static boolean loreContains(List<String> lore, String needle, boolean ignoreCase) {
        if (lore == null) {
            return false;
        }
        for (String line : lore) {
            if (contains(line, needle, ignoreCase)) {
                return true;
            }
        }
        return false;
    }

    private static boolean equal(String left, String right, boolean ignoreCase) {
        return ignoreCase ? left.equalsIgnoreCase(right) : left.equals(right);
    }

    private static boolean contains(String text, String part, boolean ignoreCase) {
        if (!ignoreCase) {
            return text.contains(part);
        }
        for (int start = 0; start + part.length() <= text.length(); start++) {
            if (text.regionMatches(true, start, part, 0, part.length())) {
                return true;
            }
        }
        return false;
    }

    private static String colored(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static Method localeReader() {
        try {
            return Player.class.getMethod("getLocale");
        } catch (ReflectiveOperationException | RuntimeException error) {
            return null;
        }
    }
}
