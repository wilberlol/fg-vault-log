package com.fg.vaultlog;

import java.io.File;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class FGVaultLogPlugin extends JavaPlugin {
    private VaultLogConfig config;
    private TransactionStore transactionStore;
    private EconomyHook economyHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = VaultLogConfig.load(this);

        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("找不到 Vault，FGVaultLog 將停用。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        transactionStore = new TransactionStore(this, new File(getDataFolder(), "transactions.db"));
        try {
            transactionStore.open();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "無法開啟 Vault 金流 SQLite 資料庫，FGVaultLog 將停用。", ex);
            transactionStore.close();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        VaultLogCommand command = new VaultLogCommand(this);
        PluginCommand pluginCommand = getCommand("vaultlog");
        if (pluginCommand == null) {
            getLogger().severe("plugin.yml 缺少 vaultlog command，FGVaultLog 將停用。");
            transactionStore.close();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        economyHook = new EconomyHook(this, transactionStore);
        economyHook.start();
        getLogger().info("FGVaultLog 已啟動；資料庫: " + transactionStore.databaseFile().getAbsolutePath());
    }

    @Override
    public void onDisable() {
        if (economyHook != null) {
            economyHook.stop();
            economyHook = null;
        }
        if (transactionStore != null) {
            transactionStore.close();
            transactionStore = null;
        }
    }

    public VaultLogConfig currentConfig() {
        return config;
    }

    public TransactionStore transactionStore() {
        return transactionStore;
    }

    public EconomyHook economyHook() {
        return economyHook;
    }

    public void reloadSettings() {
        reloadConfig();
        config = VaultLogConfig.load(this);
        if (economyHook != null) {
            economyHook.reload();
        }
        getLogger().info("FGVaultLog 設定已重新載入。");
    }
}
