package ru.privatenull.pnlibrary.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Shared optional Vault and PlayerPoints integration. */
public final class EconomyService {

    public interface Currency {
        String id();
        boolean available();
        boolean supports(double amount);
        double balance(OfflinePlayer player);
        boolean has(OfflinePlayer player, double amount);
        boolean withdraw(OfflinePlayer player, double amount);
        boolean deposit(OfflinePlayer player, double amount);
        String format(double amount);
    }

    private final JavaPlugin plugin;
    private Currency vault;
    private Currency playerPoints;

    private EconomyService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        refresh();
    }

    public static EconomyService create(JavaPlugin plugin) {
        return new EconomyService(plugin);
    }

    public Currency vault() {
        if (!vault.available() && plugin.getServer().getPluginManager().isPluginEnabled("Vault")) refreshVault();
        return vault;
    }

    public Currency playerPoints() {
        if (!playerPoints.available()
                && plugin.getServer().getPluginManager().isPluginEnabled("PlayerPoints")) refreshPlayerPoints();
        return playerPoints;
    }

    public Currency find(String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "vault", "money", "economy" -> vault();
            case "playerpoints", "player-points", "points", "pp" -> playerPoints();
            default -> new UnavailableCurrency(normalized.isBlank() ? "unknown" : normalized);
        };
    }

    public List<Currency> currencies() {
        return List.of(vault(), playerPoints());
    }

    public void refresh() {
        refreshVault();
        refreshPlayerPoints();
    }

    private void refreshVault() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("Vault")) {
            vault = new UnavailableCurrency("vault");
            return;
        }
        try {
            vault = new VaultCurrency(plugin);
        } catch (RuntimeException | LinkageError ignored) {
            vault = new UnavailableCurrency("vault");
        }
    }

    private void refreshPlayerPoints() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("PlayerPoints")) {
            playerPoints = new UnavailableCurrency("playerpoints");
            return;
        }
        try {
            playerPoints = new PointsCurrency();
        } catch (RuntimeException | LinkageError ignored) {
            playerPoints = new UnavailableCurrency("playerpoints");
        }
    }

    private static boolean valid(double amount) {
        return Double.isFinite(amount) && amount > 0.0;
    }

    private static final class VaultCurrency implements Currency {
        private final JavaPlugin plugin;

        private VaultCurrency(JavaPlugin plugin) {
            this.plugin = plugin;
        }

        @Override public String id() { return "vault"; }
        @Override public boolean available() { return economy() != null; }
        @Override public boolean supports(double amount) { return valid(amount); }

        @Override
        public double balance(OfflinePlayer player) {
            Economy economy = economy();
            return economy == null || player == null ? 0.0 : economy.getBalance(player);
        }

        @Override
        public boolean has(OfflinePlayer player, double amount) {
            Economy economy = economy();
            return economy != null && player != null && supports(amount) && economy.has(player, amount);
        }

        @Override
        public boolean withdraw(OfflinePlayer player, double amount) {
            Economy economy = economy();
            return success(economy == null || player == null || !supports(amount)
                    ? null : economy.withdrawPlayer(player, amount));
        }

        @Override
        public boolean deposit(OfflinePlayer player, double amount) {
            Economy economy = economy();
            return success(economy == null || player == null || !supports(amount)
                    ? null : economy.depositPlayer(player, amount));
        }

        @Override
        public String format(double amount) {
            Economy economy = economy();
            if (economy != null) {
                try {
                    return economy.format(amount);
                } catch (RuntimeException ignored) {
                }
            }
            return "$" + decimal(amount);
        }

        private Economy economy() {
            try {
                RegisteredServiceProvider<Economy> registration =
                        plugin.getServer().getServicesManager().getRegistration(Economy.class);
                return registration == null ? null : registration.getProvider();
            } catch (RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        private static boolean success(EconomyResponse response) {
            return response != null && response.transactionSuccess();
        }
    }

    private static final class PointsCurrency implements Currency {
        @Override public String id() { return "playerpoints"; }
        @Override public boolean available() { return api() != null; }
        @Override public boolean supports(double amount) {
            return valid(amount) && amount <= Integer.MAX_VALUE && Math.rint(amount) == amount;
        }

        @Override
        public double balance(OfflinePlayer player) {
            PlayerPointsAPI api = api();
            return api == null || player == null ? 0.0 : api.look(player.getUniqueId());
        }

        @Override
        public boolean has(OfflinePlayer player, double amount) {
            return player != null && supports(amount) && balance(player) >= amount;
        }

        @Override
        public boolean withdraw(OfflinePlayer player, double amount) {
            PlayerPointsAPI api = api();
            return api != null && player != null && supports(amount)
                    && api.take(player.getUniqueId(), (int) amount);
        }

        @Override
        public boolean deposit(OfflinePlayer player, double amount) {
            PlayerPointsAPI api = api();
            return api != null && player != null && supports(amount)
                    && api.give(player.getUniqueId(), (int) amount);
        }

        @Override
        public String format(double amount) {
            int points = supports(amount) ? (int) amount : 0;
            PlayerPointsAPI api = api();
            if (api != null) {
                try {
                    return points + " " + api.getCurrencyName(points);
                } catch (RuntimeException ignored) {
                }
            }
            return points + " поинтов";
        }

        private static PlayerPointsAPI api() {
            try {
                PlayerPoints plugin = PlayerPoints.getInstance();
                return plugin == null ? null : plugin.getAPI();
            } catch (RuntimeException | LinkageError ignored) {
                return null;
            }
        }
    }

    private record UnavailableCurrency(String id) implements Currency {
        @Override public boolean available() { return false; }
        @Override public boolean supports(double amount) { return false; }
        @Override public double balance(OfflinePlayer player) { return 0.0; }
        @Override public boolean has(OfflinePlayer player, double amount) { return false; }
        @Override public boolean withdraw(OfflinePlayer player, double amount) { return false; }
        @Override public boolean deposit(OfflinePlayer player, double amount) { return false; }
        @Override public String format(double amount) { return decimal(amount); }
    }

    private static String decimal(double amount) {
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(amount);
    }
}
