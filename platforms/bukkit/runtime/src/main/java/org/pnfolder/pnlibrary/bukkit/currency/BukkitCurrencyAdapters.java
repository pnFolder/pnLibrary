package org.pnfolder.pnlibrary.bukkit.currency;

import java.lang.reflect.InvocationTargetException;

import org.bukkit.plugin.Plugin;
import org.pnfolder.pnlibrary.api.currency.CurrencyProvider;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.common.lang.Attempt;
import org.pnfolder.pnlibrary.common.lang.Failures;

/** Discovers optional economy plugins without linking their APIs on unsupported servers. */
public final class BukkitCurrencyAdapters {
    private BukkitCurrencyAdapters() {
    }

    @SuppressWarnings("unchecked")
    public static CurrencyProvider vault(Plugin plugin, PnLogger log) {
        Attempt<CurrencyProvider> attempt = Failures.runCatchingNonFatal(() -> {
            Class<Object> type = (Class<Object>) Class.forName("net.milkbowl.vault.economy.Economy");
            Object economy = plugin.getServer().getServicesManager().load(type);
            return economy == null ? null : vaultCurrency(economy);
        });
        if (attempt.isFailure() && !(attempt.exceptionOrNull() instanceof ClassNotFoundException)) {
            log.error("Vault is installed but its economy could not be connected", attempt.exceptionOrNull());
        }
        return attempt.getOrNull();
    }

    /**
     * Returns {@code null} while the economy plugin is still starting: Essentials registers its Vault provider
     * before its settings load, so the first read fails with a NullPointerException. The caller retries on the next
     * plugin or service event.
     */
    static CurrencyProvider vaultCurrency(Object economy) throws InvocationTargetException {
        try {
            return new ReflectiveVaultCurrency(economy);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof NullPointerException) {
                return null;
            }
            throw failure;
        }
    }

    public static CurrencyProvider playerPoints(Plugin plugin, PnLogger log) {
        Attempt<CurrencyProvider> attempt = Failures.runCatchingNonFatal(() -> {
            Plugin target = plugin.getServer().getPluginManager().getPlugin("PlayerPoints");
            if (target == null) {
                return null;
            }
            Object api = null;
            for (java.lang.reflect.Method method : target.getClass().getMethods()) {
                if (method.getName().equals("getAPI") && method.getParameterTypes().length == 0) {
                    api = method.invoke(target);
                    break;
                }
            }
            return api == null ? null : new ReflectivePlayerPointsCurrency(api);
        });
        if (attempt.isFailure()) {
            log.error("PlayerPoints is installed but its currency could not be connected", attempt.exceptionOrNull());
        }
        return attempt.getOrNull();
    }
}
