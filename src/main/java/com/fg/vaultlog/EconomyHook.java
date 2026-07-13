package com.fg.vaultlog;

import java.util.Collection;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitTask;

/** Maintains the proxy while Economy providers are loaded, replaced, or reloaded. */
public final class EconomyHook {
    private final FGVaultLogPlugin plugin;
    private final TransactionStore store;
    private final TransactionDeduplicator deduplicator;
    private BalanceSnapshotMonitor snapshotMonitor;

    private BukkitTask retryTask;
    private Economy delegate;
    private LoggingEconomy proxy;
    private boolean missingProviderLogged;

    public EconomyHook(FGVaultLogPlugin plugin, TransactionStore store, TransactionDeduplicator deduplicator) {
        this.plugin = plugin;
        this.store = store;
        this.deduplicator = deduplicator;
    }

    public void setSnapshotMonitor(BalanceSnapshotMonitor snapshotMonitor) {
        this.snapshotMonitor = snapshotMonitor;
    }

    public void start() {
        reschedule();
        tryHook();
    }

    public void reload() {
        reschedule();
        tryHook();
    }

    public void stop() {
        if (retryTask != null) {
            retryTask.cancel();
            retryTask = null;
        }
        unhook();
    }

    public String hookedProviderName() {
        if (delegate == null) {
            return "未掛接";
        }
        try {
            return delegate.getName();
        } catch (RuntimeException ex) {
            return delegate.getClass().getSimpleName();
        }
    }

    public boolean isHooked() {
        return proxy != null && delegate != null;
    }

    public Economy observedProvider() {
        return delegate;
    }

    public String proxyPriority() {
        return plugin.currentConfig().priority().name();
    }

    private void reschedule() {
        if (retryTask != null) {
            retryTask.cancel();
        }
        VaultLogConfig config = plugin.currentConfig();
        retryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tryHook, 1L, config.hookIntervalTicks());
    }

    private void tryHook() {
        VaultLogConfig config = plugin.currentConfig();
        if (!config.proxyEnabled()) {
            unhook();
            return;
        }

        Economy provider = findProvider();
        if (provider == null) {
            if (!missingProviderLogged) {
                plugin.getLogger().warning("尚未找到 Vault Economy provider，將持續重試；請確認 EssentialsX Economy/CMI 等經濟插件已啟用。");
                missingProviderLogged = true;
            }
            return;
        }
        missingProviderLogged = false;

        if (provider == delegate && proxy != null) {
            return;
        }
        unhook();
        proxy = new LoggingEconomy(plugin, provider, store, plugin::currentConfig, deduplicator, snapshotMonitor);
        delegate = provider;
        ServicesManager services = Bukkit.getServicesManager();
        services.register(Economy.class, proxy, plugin, config.priority());
        plugin.getLogger().info("已掛接 Vault Economy provider: " + safeName(provider)
                + "，代理優先權: " + config.priority().name());
    }

    private Economy findProvider() {
        Collection<RegisteredServiceProvider<Economy>> registrations = Bukkit.getServicesManager()
                .getRegistrations(Economy.class);
        RegisteredServiceProvider<Economy> best = null;
        int bestPriority = Integer.MIN_VALUE;
        for (RegisteredServiceProvider<Economy> registration : registrations) {
            Economy provider = registration.getProvider();
            if (provider == null || provider instanceof LoggingEconomy || registration.getPlugin() == plugin) {
                continue;
            }
            ServicePriority priority = registration.getPriority();
            int rank = priority == null ? 0 : priority.ordinal();
            if (best == null || rank > bestPriority) {
                best = registration;
                bestPriority = rank;
            }
        }
        return best == null ? null : best.getProvider();
    }

    private void unhook() {
        if (proxy != null) {
            Bukkit.getServicesManager().unregister(Economy.class, proxy);
        }
        proxy = null;
        delegate = null;
    }

    private static String safeName(Economy provider) {
        try {
            return provider.getName();
        } catch (RuntimeException ex) {
            return provider.getClass().getName();
        }
    }
}
