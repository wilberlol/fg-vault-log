package com.fg.vaultlog;

import java.util.Locale;
import java.util.Objects;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;

/** Parsed, validated runtime configuration. */
public record VaultLogConfig(
        boolean proxyEnabled,
        ServicePriority priority,
        long hookIntervalTicks,
        boolean includeFailed,
        boolean includeBankTransactions,
        int pageSize
) {

    public static VaultLogConfig load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();

        boolean proxyEnabled = config.getBoolean("proxy.enabled", true);
        ServicePriority priority = parsePriority(plugin, config.getString("proxy.priority", "HIGHEST"));
        long interval = clamp(config.getLong("proxy.hook-interval-ticks", 100L), 20L, 20L * 60L);
        boolean includeFailed = config.getBoolean("logging.include-failed", true);
        boolean includeBanks = config.getBoolean("logging.include-bank-transactions", true);
        int pageSize = (int) clamp(config.getInt("query.page-size", 8), 1, 50);

        return new VaultLogConfig(proxyEnabled, priority, interval, includeFailed, includeBanks, pageSize);
    }

    private static ServicePriority parsePriority(JavaPlugin plugin, String raw) {
        String value = Objects.requireNonNullElse(raw, "HIGHEST").trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "LOWEST" -> ServicePriority.Lowest;
            case "LOW" -> ServicePriority.Low;
            case "NORMAL" -> ServicePriority.Normal;
            case "HIGH" -> ServicePriority.High;
            case "HIGHEST" -> ServicePriority.Highest;
            default -> {
                plugin.getLogger().warning("無效的 proxy.priority: " + raw + "，改用 HIGHEST");
                yield ServicePriority.Highest;
            }
        };
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
