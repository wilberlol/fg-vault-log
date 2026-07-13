package com.fg.vaultlog;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public final class VaultLogCommand implements CommandExecutor, TabCompleter {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final FGVaultLogPlugin plugin;

    public VaultLogCommand(FGVaultLogPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vaultlog.admin")) {
            sender.sendMessage(ChatColor.RED + "你沒有權限使用此指令。");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> sendStatus(sender);
            case "reload" -> {
                plugin.reloadSettings();
                sender.sendMessage(ChatColor.GREEN + "FGVaultLog 設定已重新載入。");
            }
            case "latest", "recent" -> queryLatest(sender, args);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void sendStatus(CommandSender sender) {
        EconomyHook hook = plugin.economyHook();
        sender.sendMessage(ChatColor.GOLD + "[FGVaultLog] 狀態");
        sender.sendMessage(ChatColor.GRAY + "代理: "
                + (plugin.currentConfig().proxyEnabled() ? ChatColor.GREEN + "啟用" : ChatColor.RED + "停用"));
        sender.sendMessage(ChatColor.GRAY + "掛接: "
                + (hook != null && hook.isHooked() ? ChatColor.GREEN + "已掛接" : ChatColor.RED + "未掛接"));
        sender.sendMessage(ChatColor.GRAY + "Provider: " + ChatColor.WHITE
                + (hook == null ? "未啟動" : hook.hookedProviderName()));
        CmiBalanceBridge cmiBridge = plugin.cmiBridge();
        sender.sendMessage(ChatColor.GRAY + "CMI event: " + ChatColor.WHITE
                + (cmiBridge != null && cmiBridge.isRegistered() ? "registered" : "not registered"));
        BalanceSnapshotMonitor snapshotMonitor = plugin.snapshotMonitor();
        sender.sendMessage(ChatColor.GRAY + "Fallback snapshot: " + ChatColor.WHITE
                + (snapshotMonitor != null && snapshotMonitor.isActive() ? "active" : "disabled"));
        sender.sendMessage(ChatColor.GRAY + "代理優先權: " + ChatColor.WHITE
                + (hook == null ? "unknown" : hook.proxyPriority()));
        sender.sendMessage(ChatColor.GRAY + "資料庫: " + ChatColor.WHITE
                + plugin.transactionStore().databaseFile().getAbsolutePath());
    }

    private void queryLatest(CommandSender sender, String[] args) {
        String account = null;
        int page = 1;
        if (args.length == 2) {
            if (isPositiveInteger(args[1])) {
                page = Integer.parseInt(args[1]);
            } else {
                account = args[1];
            }
        } else if (args.length >= 3) {
            account = args[1];
            if (!isPositiveInteger(args[2])) {
                sender.sendMessage(ChatColor.RED + "頁數必須是正整數。");
                return;
            }
            page = Integer.parseInt(args[2]);
        }
        page = Math.max(1, Math.min(page, 10_000));

        int pageSize = plugin.currentConfig().pageSize();
        final int queryPage = page;
        int offset = (queryPage - 1) * pageSize;
        String queryLabel = account == null ? "全部" : account;
        sender.sendMessage(ChatColor.GRAY + "正在查詢 " + queryLabel + " 的第 " + queryPage + " 頁...");
        plugin.transactionStore().queryLatest(account, pageSize, offset)
                .whenComplete((rows, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        Throwable cause = error instanceof CompletionException && error.getCause() != null
                                ? error.getCause() : error;
                        sender.sendMessage(ChatColor.RED + "查詢失敗: " + cause.getMessage());
                        return;
                    }
                    sendRows(sender, rows, queryPage, queryLabel);
                }));
    }

    private void sendRows(CommandSender sender, List<VaultTransaction> rows, int page, String queryLabel) {
        sender.sendMessage(ChatColor.GOLD + "[FGVaultLog] " + queryLabel + " / 第 " + page + " 頁");
        if (rows.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "沒有紀錄。");
            return;
        }
        for (VaultTransaction row : rows) {
            String status = row.success() ? ChatColor.GREEN + "成功" : ChatColor.RED + "失敗";
            String sign = row.operation().isPositive(row.amount()) ? "+" : "-";
            String amount = String.format(Locale.ROOT, "%.2f", Math.abs(row.amount()));
            String account = row.accountLabel();
            String world = row.world() == null ? "-" : row.world();
            sender.sendMessage(ChatColor.DARK_GRAY + TIME_FORMAT.format(row.occurredAt())
                    + ChatColor.GRAY + " " + status
                    + ChatColor.WHITE + " " + row.operation().name()
                    + ChatColor.AQUA + " " + account
                    + ChatColor.YELLOW + " " + sign + amount
                    + ChatColor.GRAY + " world=" + world
                    + ChatColor.DARK_GRAY + " provider=" + row.provider()
                    + " tx=" + shortId(row.id()));
            sender.sendMessage(ChatColor.GRAY + "  event=" + row.eventName()
                    + " sourcePlugin=" + row.sourcePlugin()
                    + " sourceCall=" + row.sourceClass());
            sender.sendMessage(ChatColor.GRAY + "  balance=" + formatBalance(row.balanceBefore())
                    + " -> " + formatBalance(row.balanceAfter())
                    + " requested=" + formatMoney(row.requestedAmount())
                    + " applied=" + formatMoney(row.amount()));
            if (!row.success() && row.message() != null && !row.message().isBlank()) {
                sender.sendMessage(ChatColor.RED + "  原因: " + row.message());
            }
        }
    }

    private static void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "[FGVaultLog] 指令");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " status" + ChatColor.GRAY + " - 查看 Vault provider 掛接狀態");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " latest [玩家] [頁數]" + ChatColor.GRAY + " - 查詢玩家金流與詳細 event");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " reload" + ChatColor.GRAY + " - 重新載入設定");
    }

    private static String shortId(String id) {
        return id == null ? "unknown" : id.substring(0, Math.min(8, id.length()));
    }

    private static String formatMoney(double amount) {
        return String.format(Locale.ROOT, "%.2f", amount);
    }

    private static String formatBalance(Double amount) {
        return amount == null ? "unknown" : formatMoney(amount);
    }

    private static boolean isPositiveInteger(String value) {
        try {
            return Integer.parseInt(value) > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("vaultlog.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Arrays.stream(new String[]{"status", "latest", "reload", "help"})
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        return new ArrayList<>();
    }
}
