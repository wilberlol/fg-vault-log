package com.fg.vaultlog;

import com.fg.vaultlog.audit.CallerResolver;
import com.fg.vaultlog.audit.CallerResolver.Source;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

/** Transparent Vault Economy proxy that records mutating calls before returning the provider response. */
public final class LoggingEconomy implements Economy {
    private final Plugin owner;
    private final Economy delegate;
    private final TransactionStore store;
    private final Supplier<VaultLogConfig> configSupplier;
    private final TransactionDeduplicator deduplicator;
    private final BalanceSnapshotMonitor snapshotMonitor;
    private final CallerResolver callerResolver;

    public LoggingEconomy(Plugin owner, Economy delegate, TransactionStore store,
                          Supplier<VaultLogConfig> configSupplier,
                          TransactionDeduplicator deduplicator,
                          BalanceSnapshotMonitor snapshotMonitor) {
        this.owner = owner;
        this.delegate = delegate;
        this.store = store;
        this.configSupplier = configSupplier;
        this.deduplicator = deduplicator;
        this.snapshotMonitor = snapshotMonitor;
        this.callerResolver = new CallerResolver(owner);
    }

    public Economy delegate() {
        return delegate;
    }

    @Override
    public boolean isEnabled() {
        return delegate.isEnabled();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public boolean hasBankSupport() {
        return delegate.hasBankSupport();
    }

    @Override
    public int fractionalDigits() {
        return delegate.fractionalDigits();
    }

    @Override
    public String format(double amount) {
        return delegate.format(amount);
    }

    @Override
    public String currencyNamePlural() {
        return delegate.currencyNamePlural();
    }

    @Override
    public String currencyNameSingular() {
        return delegate.currencyNameSingular();
    }

    @Override
    public boolean hasAccount(String playerName) {
        return delegate.hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return delegate.hasAccount(player);
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return delegate.hasAccount(playerName, worldName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return delegate.hasAccount(player, worldName);
    }

    @Override
    public double getBalance(String playerName) {
        return delegate.getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return delegate.getBalance(player);
    }

    @Override
    public double getBalance(String playerName, String worldName) {
        return delegate.getBalance(playerName, worldName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String worldName) {
        return delegate.getBalance(player, worldName);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return delegate.has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return delegate.has(player, amount);
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return delegate.has(playerName, worldName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return delegate.has(player, worldName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return mutatePlayer(TransactionOperation.WITHDRAW_PLAYER, "Economy#withdrawPlayer(String,double)", player(playerName), null, amount,
                () -> delegate.getBalance(playerName), () -> delegate.withdrawPlayer(playerName, amount));
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return mutatePlayer(TransactionOperation.WITHDRAW_PLAYER, "Economy#withdrawPlayer(OfflinePlayer,double)", player(player), null, amount,
                () -> delegate.getBalance(player), () -> delegate.withdrawPlayer(player, amount));
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return mutatePlayer(TransactionOperation.WITHDRAW_PLAYER, "Economy#withdrawPlayer(String,String,double)", player(playerName), worldName, amount,
                () -> delegate.getBalance(playerName, worldName),
                () -> delegate.withdrawPlayer(playerName, worldName, amount));
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return mutatePlayer(TransactionOperation.WITHDRAW_PLAYER, "Economy#withdrawPlayer(OfflinePlayer,String,double)", player(player), worldName, amount,
                () -> delegate.getBalance(player, worldName),
                () -> delegate.withdrawPlayer(player, worldName, amount));
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return mutatePlayer(TransactionOperation.DEPOSIT_PLAYER, "Economy#depositPlayer(String,double)", player(playerName), null, amount,
                () -> delegate.getBalance(playerName), () -> delegate.depositPlayer(playerName, amount));
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return mutatePlayer(TransactionOperation.DEPOSIT_PLAYER, "Economy#depositPlayer(OfflinePlayer,double)", player(player), null, amount,
                () -> delegate.getBalance(player), () -> delegate.depositPlayer(player, amount));
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return mutatePlayer(TransactionOperation.DEPOSIT_PLAYER, "Economy#depositPlayer(String,String,double)", player(playerName), worldName, amount,
                () -> delegate.getBalance(playerName, worldName),
                () -> delegate.depositPlayer(playerName, worldName, amount));
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return mutatePlayer(TransactionOperation.DEPOSIT_PLAYER, "Economy#depositPlayer(OfflinePlayer,String,double)", player(player), worldName, amount,
                () -> delegate.getBalance(player, worldName),
                () -> delegate.depositPlayer(player, worldName, amount));
    }

    @Override
    public EconomyResponse bankWithdraw(String bankName, double amount) {
        return delegate.bankWithdraw(bankName, amount);
    }

    @Override
    public EconomyResponse bankDeposit(String bankName, double amount) {
        return delegate.bankDeposit(bankName, amount);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return delegate.createBank(name, player);
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return delegate.createBank(name, player);
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return delegate.deleteBank(name);
    }

    @Override
    public EconomyResponse bankBalance(String bankName) {
        return delegate.bankBalance(bankName);
    }

    @Override
    public EconomyResponse bankHas(String bankName, double amount) {
        return delegate.bankHas(bankName, amount);
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return delegate.isBankOwner(name, playerName);
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return delegate.isBankOwner(name, player);
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return delegate.isBankMember(name, playerName);
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return delegate.isBankMember(name, player);
    }

    @Override
    public List<String> getBanks() {
        return delegate.getBanks();
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return delegate.createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return delegate.createPlayerAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return delegate.createPlayerAccount(playerName, worldName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return delegate.createPlayerAccount(player, worldName);
    }

    private EconomyResponse mutatePlayer(TransactionOperation operation, String eventName, AccountRef account, String world,
                                         double requestedAmount, BalanceCall beforeCall, EconomyCall call) {
        Source source = callerResolver.resolve();
        Double before = safeBalance(beforeCall);
        deduplicator.expect(account.id(), account.name(), expectedDelta(operation, requestedAmount));
        EconomyResponse response;
        try {
            response = call.invoke();
        } catch (RuntimeException ex) {
            record(operation, eventName, AccountType.PLAYER, account, world, requestedAmount, requestedAmount,
                    before, null, false, "EXCEPTION", message(ex), source);
            throw ex;
        }
        recordResponse(operation, eventName, AccountType.PLAYER, account, world, requestedAmount, before, response, source);
        observeResponse(account, response);
        return response;
    }
    private void recordResponse(TransactionOperation operation, String eventName, AccountType accountType, AccountRef account,
                                String world, double requestedAmount, Double before,
                                EconomyResponse response, Source source) {
        boolean success = response != null && response.transactionSuccess();
        VaultLogConfig config = configSupplier.get();
        if (!success && !config.includeFailed()) {
            return;
        }
        double applied = response == null || !Double.isFinite(response.amount)
                ? requestedAmount : response.amount;
        Double after = response == null || !Double.isFinite(response.balance) ? null : response.balance;
        String responseType = response == null || response.type == null ? "NULL" : response.type.name();
        String errorMessage = response == null ? "provider returned null response" : response.errorMessage;
        record(operation, eventName, accountType, account, world, requestedAmount, applied, before, after,
                success, responseType, errorMessage, source);
    }

    private void record(TransactionOperation operation, String eventName, AccountType accountType, AccountRef account, String world,
                        double requestedAmount, double appliedAmount, Double before, Double after,
                        boolean success, String responseType, String message, Source source) {
        String providerName;
        try {
            providerName = delegate.getName();
        } catch (RuntimeException ex) {
            providerName = "unknown";
        }
        store.record(new VaultTransaction(
                UUID.randomUUID().toString(),
                Instant.now(),
                operation,
                eventName,
                accountType,
                account.id(),
                account.name(),
                blankToNull(world),
                requestedAmount,
                appliedAmount,
                before,
                after,
                success,
                responseType,
                message,
                providerName,
                source.plugin(),
                source.callSite()
        ));
    }

    private Double safeBalance(BalanceCall call) {
        try {
            double value = call.get();
            return Double.isFinite(value) ? value : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String message(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static double expectedDelta(TransactionOperation operation, double amount) {
        double absolute = Math.abs(amount);
        return operation == TransactionOperation.WITHDRAW_PLAYER ? -absolute : absolute;
    }

    private void observeResponse(AccountRef account, EconomyResponse response) {
        if (response == null || !Double.isFinite(response.balance)) {
            return;
        }
        if (snapshotMonitor != null) {
            snapshotMonitor.observe(account.id(), account.name(), response.balance);
        }
    }

    private static AccountRef player(String playerName) {
        String value = playerName == null ? "" : playerName;
        return new AccountRef(value, value);
    }

    private static AccountRef player(OfflinePlayer player) {
        if (player == null) {
            return new AccountRef("", null);
        }
        String id = player.getUniqueId() == null ? "" : player.getUniqueId().toString();
        return new AccountRef(id, player.getName());
    }

    @FunctionalInterface
    private interface EconomyCall {
        EconomyResponse invoke();
    }

    @FunctionalInterface
    private interface BalanceCall {
        double get();
    }

    private record AccountRef(String id, String name) {
    }
}
