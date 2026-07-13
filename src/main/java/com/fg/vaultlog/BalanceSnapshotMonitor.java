package com.fg.vaultlog;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import net.milkbowl.vault.economy.Economy;

/**
 * Best-effort fallback for providers or plugins that change balances without Vault or a native event.
 * It can detect the delta, but no generic API can reconstruct the original reason after the call ended.
 */
public final class BalanceSnapshotMonitor {
    private final Plugin plugin;
    private final TransactionStore store;
    private final EconomyHook economyHook;
    private final Map<String, Snapshot> snapshots = new HashMap<>();

    private BukkitTask task;
    private Economy activeProvider;

    public BalanceSnapshotMonitor(Plugin plugin, TransactionStore store, EconomyHook economyHook) {
        this.plugin = plugin;
        this.store = store;
        this.economyHook = economyHook;
    }

    public void start() {
        reschedule();
    }

    public void reload() {
        reschedule();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        synchronized (this) {
            snapshots.clear();
            activeProvider = null;
        }
    }

    public boolean isActive() {
        return task != null && plugin instanceof FGVaultLogPlugin fg
                && fg.currentConfig().fallbackEnabled();
    }

    /** Keeps the fallback baseline current when a more precise event/proxy row was recorded. */
    public synchronized void observe(String accountId, String accountName, double balance) {
        if (!Double.isFinite(balance)) {
            return;
        }
        Snapshot current = find(accountId, accountName);
        if (current != null) {
            snapshots.remove(current.key());
        }
        String key = normalize(accountId);
        if (key.isBlank()) {
            key = normalize(accountName);
        }
        if (!key.isBlank()) {
            snapshots.put(key, new Snapshot(key, accountId, accountName, balance));
        }
    }

    private void reschedule() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        FGVaultLogPlugin fg = plugin instanceof FGVaultLogPlugin value ? value : null;
        if (fg == null || !fg.currentConfig().fallbackEnabled()) {
            synchronized (this) {
                snapshots.clear();
                activeProvider = null;
            }
            return;
        }
        long interval = fg.currentConfig().fallbackIntervalTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::poll, interval, interval);
    }

    private void poll() {
        FGVaultLogPlugin fg = (FGVaultLogPlugin) plugin;
        if (!fg.currentConfig().fallbackEnabled()) {
            return;
        }
        Economy provider = economyHook.observedProvider();
        if (provider == null) {
            synchronized (this) {
                snapshots.clear();
                activeProvider = null;
            }
            return;
        }
        synchronized (this) {
            if (activeProvider != provider) {
                activeProvider = provider;
                snapshots.clear();
            }
        }

        Set<String> onlineKeys = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String accountId = player.getUniqueId().toString();
            String accountName = player.getName();
            onlineKeys.add(normalize(accountId));
            onlineKeys.add(normalize(accountName));

            double balance;
            try {
                balance = provider.getBalance(player);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (!Double.isFinite(balance)) {
                continue;
            }

            Snapshot previous;
            synchronized (this) {
                previous = find(accountId, accountName);
                if (previous != null) {
                    snapshots.remove(previous.key());
                }
                snapshots.put(normalize(accountId),
                        new Snapshot(normalize(accountId), accountId, accountName, balance));
            }
            if (previous == null) {
                continue;
            }

            double delta = balance - previous.balance();
            if (!Double.isFinite(delta) || Math.abs(delta) < 1.0E-6) {
                continue;
            }
            recordExternalChange(player, previous.balance(), balance, delta, provider);
        }

        synchronized (this) {
            snapshots.keySet().removeIf(key -> !onlineKeys.contains(key));
        }
    }

    private void recordExternalChange(Player player, double before, double after,
                                      double delta, Economy provider) {
        store.record(new VaultTransaction(
                UUID.randomUUID().toString(),
                Instant.now(),
                TransactionOperation.BALANCE_CHANGE,
                "UNKNOWN_EXTERNAL_CHANGE",
                AccountType.PLAYER,
                player.getUniqueId().toString(),
                player.getName(),
                player.getWorld().getName(),
                delta,
                delta,
                before,
                after,
                true,
                "POLL",
                "Direct balance change detected without a Vault/CMI event; cause unavailable.",
                providerName(provider),
                "unknown",
                "unknown"
        ));
    }

    private Snapshot find(String accountId, String accountName) {
        Snapshot direct = snapshots.get(normalize(accountId));
        if (direct != null) {
            return direct;
        }
        String normalizedName = normalize(accountName);
        if (!normalizedName.isBlank()) {
            return snapshots.get(normalizedName);
        }
        return null;
    }

    private static String providerName(Economy provider) {
        try {
            return provider.getName();
        } catch (RuntimeException ex) {
            return provider.getClass().getSimpleName();
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record Snapshot(String key, String accountId, String accountName, double balance) {
    }
}
