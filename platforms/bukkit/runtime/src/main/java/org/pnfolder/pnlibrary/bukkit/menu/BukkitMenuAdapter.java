package org.pnfolder.pnlibrary.bukkit.menu;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatReader;
import org.pnfolder.pnlibrary.api.chat.ChatNotice;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.api.platform.PlatformProvider;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;
import org.pnfolder.pnlibrary.bukkit.api.BukkitPlatform;
import org.pnfolder.pnlibrary.bukkit.inventory.MenuBuilder;
import org.pnfolder.pnlibrary.bukkit.inventory.MenuService;
import org.pnfolder.pnlibrary.bukkit.inventory.MenuSession;
import org.pnfolder.pnlibrary.bukkit.inventory.Menus;
import org.pnfolder.pnlibrary.bukkit.item.PlayerHeads;
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler;
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeBridge;
import org.pnfolder.pnlibrary.core.menu.session.ClickType;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.Menu;
import org.pnfolder.pnlibrary.core.menu.MenuItem;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;

/** Turns the portable menu into Bukkit inventories. */
public final class BukkitMenuAdapter {
    private static final ConcurrentHashMap<String, ItemStack> HEADS = new ConcurrentHashMap<>();
    /** Any of these opens {@code /pn gui}; losing all of them closes the menu. */
    private static final List<String> MENU_ACCESS = Arrays.asList("pnlibrary.admin", "pnlibrary.updates.view");
    private static final Duration RECHECK = Duration.ofSeconds(2);
    private static final List<String> LEGACY_GLASS = Arrays.asList("STAINED_GLASS_PANE", "LEGACY_STAINED_GLASS_PANE");
    private static final float SOUND_VOLUME = 0.22f;
    private static final float SOUND_PITCH = 1.15f;
    private static final Sound OPEN_SOUND = openSound();

    private final Plugin plugin;
    private final PnLibrary library;
    private final ServerScheduler scheduler;

    public BukkitMenuAdapter(Plugin plugin, PnLibrary library, ServerScheduler scheduler) {
        this.plugin = plugin;
        this.library = library;
        this.scheduler = scheduler;
    }

    /** The viewer for {@code player} on this server. */
    public MenuViewer viewer(Player player) {
        return new BukkitMenuViewer(player, library, scheduler, this);
    }

    /** The player behind {@code viewer}, or null when it is not a local one. */
    public Player playerOf(MenuViewer viewer) {
        return viewer instanceof BukkitMenuViewer ? ((BukkitMenuViewer) viewer).getNative() : null;
    }

    /** The stack for {@code spec}: the head when the server can make one, otherwise the ordinary item. */
    @SuppressWarnings("deprecation")
    public ItemStack stack(ItemSpec spec) {
        String name = HexColors.downgrade(spec.getName());
        List<String> lore = new ArrayList<>();
        for (String line : spec.getLore()) {
            lore.add(HexColors.downgrade(line));
        }
        ItemStack head = spec.getTexture() == null ? null : head(spec.getTexture());
        ItemStack base = head != null ? head : new ItemStack(materialOf(spec));
        if (head == null && spec.getLegacyData() != 0 && LEGACY_GLASS.contains(base.getType().name())) {
            base.setDurability(spec.getLegacyData());
        }
        ItemMeta meta = base.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            for (ItemFlag flag : ItemFlag.values()) {
                meta.addItemFlags(flag);
            }
            base.setItemMeta(meta);
        }
        return base;
    }

    private Material materialOf(ItemSpec spec) {
        for (String candidate : Arrays.asList(spec.getMaterial(), spec.getAlternative())) {
            Material resolved = candidate == null ? null : resolve(candidate);
            if (resolved != null) {
                return resolved;
            }
        }
        return Material.PAPER;
    }

    /**
     * A copy of the textured player head for {@code hash}, built once per texture and cached after that. A head the
     * server cannot make is {@code null}, and the caller shows an ordinary item instead.
     */
    private ItemStack head(String hash) {
        ItemStack cached = HEADS.get(hash);
        if (cached != null) {
            return cached.clone();
        }
        ItemStack made = PlayerHeads.textured(hash);
        if (made != null) {
            HEADS.put(hash, made.clone());
        }
        return made;
    }

    /** The inventory page for {@code menu}; a click goes to its handler as a click by the local player. */
    public org.pnfolder.pnlibrary.bukkit.inventory.Menu page(Menu menu) {
        MenuBuilder builder = Menus.chest(menu.getTitle()).rows(menu.getRows());
        for (Map.Entry<Integer, MenuItem> entry : menu.getItems().entrySet()) {
            int slot = entry.getKey();
            ItemStack item = entry.getValue().getItem() == null ? null : stack(entry.getValue().getItem());
            org.pnfolder.pnlibrary.core.menu.session.MenuClickHandler handler = entry.getValue().getHandler();
            if (handler == null) {
                builder.slot(slot, item);
            } else if (item != null) {
                builder.button(slot, item, click -> {
                    if (mayUse(click.getPlayer())) {
                        handler.handle(new org.pnfolder.pnlibrary.core.menu.session.MenuClick(
                            viewer(click.getPlayer()), slot, kind(click.getClick())));
                    } else {
                        revoke(click.getPlayer(), click.getSession());
                    }
                });
            }
        }
        return builder.build();
    }

    /** Opens {@code menu} for {@code player} with the quiet chest sound of the menu. */
    public void open(Player player, Menu menu) {
        MenuService menus = PlatformProvider.of(library, BukkitPlatform.class).getMenus();
        MenuSession session = menus.open(plugin, player, page(menu));
        watch(player, session);
        if (OPEN_SOUND != null) {
            player.playSound(player.getLocation(), OPEN_SOUND, SOUND_VOLUME, SOUND_PITCH);
        }
    }

    /** Whether {@code player} may still use the menu: the permissions {@code /pn gui} itself asks for. */
    private boolean mayUse(Player player) {
        for (String permission : MENU_ACCESS) {
            if (player.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rechecks the right to the menu while {@code session} is open, so a player who loses it (for example when a
     * permission plugin takes the group away) is not left with a working menu. The task ends with the session.
     */
    private void watch(Player player, MenuSession session) {
        MenuService menus = PlatformProvider.of(library, BukkitPlatform.class).getMenus();
        RuntimeBridge.runtimeTaskScope(library).schedule(
            TaskSpec.builder()
                .execution(TaskExecution.entity(player))
                .delay(RECHECK)
                .interval(RECHECK)
                .cancellable(false)
                .hidden(true)
                .cancelWhen(() -> !player.isOnline() || menus.session(player) != session)
                .action(context -> {
                    if (!mayUse(player)) {
                        revoke(player, session);
                    }
                })
                .build());
    }

    /** Closes {@code session} because {@code player} no longer holds the right to the menu, and says so. */
    private void revoke(Player player, MenuSession session) {
        session.close();
        ChatNotice notice = new ChatNotice(ChatTone.FAILURE, "Меню закрыто: не хватает права.", MENU_ACCESS.get(0));
        Audience audience = library.getAudiences().player(player.getUniqueId());
        if (audience != null) {
            for (Component line : new ChatKit(ChatReader.PLAYER).notice(notice)) {
                audience.sendMessage(line);
            }
        }
    }

    private static ClickType kind(org.bukkit.event.inventory.ClickType click) {
        for (ClickType type : ClickType.values()) {
            if (type.name().equals(click.name())) {
                return type;
            }
        }
        return ClickType.OTHER;
    }

    private static Material resolve(String name) {
        Material material = null;
        try {
            material = Material.getMaterial(name);
        } catch (RuntimeException error) {
            // Fall through to the next lookup.
        }
        if (material == null) {
            material = Material.matchMaterial(name);
        }
        if (material == null) {
            try {
                material = Material.getMaterial("LEGACY_" + name);
            } catch (RuntimeException error) {
                // No such material on this server.
            }
        }
        return material;
    }

    private static Sound openSound() {
        for (String name : Arrays.asList("BLOCK_BARREL_OPEN", "BLOCK_CHEST_OPEN", "CHEST_OPEN")) {
            for (Sound sound : Sound.values()) {
                if (sound.name().equals(name)) {
                    return sound;
                }
            }
        }
        return null;
    }
}
