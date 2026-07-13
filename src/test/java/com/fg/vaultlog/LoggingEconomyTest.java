package com.fg.vaultlog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoggingEconomyTest {
    @Test
    void forwardsPlayerCallsButDoesNotRecordBankCalls(@TempDir Path tempDirectory) throws Exception {
        TransactionStore store = new TransactionStore(Logger.getLogger("FGVaultLogProxyTest"),
                tempDirectory.resolve("transactions.db").toFile());
        store.open();

        AtomicInteger deposits = new AtomicInteger();
        AtomicInteger bankDeposits = new AtomicInteger();
        Economy delegate = (Economy) Proxy.newProxyInstance(
                Economy.class.getClassLoader(),
                new Class<?>[]{Economy.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "TestEconomy";
                    case "getBalance" -> 100.0;
                    case "depositPlayer" -> {
                        deposits.incrementAndGet();
                        yield new EconomyResponse(25.0, 125.0,
                                EconomyResponse.ResponseType.SUCCESS, null);
                    }
                    case "bankDeposit" -> {
                        bankDeposits.incrementAndGet();
                        yield new EconomyResponse(50.0, 150.0,
                                EconomyResponse.ResponseType.SUCCESS, null);
                    }
                    case "isEnabled" -> true;
                    case "hasBankSupport" -> true;
                    case "fractionalDigits" -> 2;
                    case "currencyNamePlural", "currencyNameSingular", "format" -> "money";
                    default -> defaultValue(method.getReturnType());
                });

        VaultLogConfig config = new VaultLogConfig(true, ServicePriority.Highest, 100, true, 8);
        LoggingEconomy loggingEconomy = new LoggingEconomy(null, delegate, store, () -> config);
        loggingEconomy.depositPlayer("Steve", 25.0);
        loggingEconomy.bankDeposit("guild", 50.0);

        List<VaultTransaction> rows = store.queryLatest(null, 10, 0).get(5, TimeUnit.SECONDS);
        assertEquals(1, deposits.get());
        assertEquals(1, bankDeposits.get());
        assertEquals(1, rows.size());
        assertEquals(TransactionOperation.DEPOSIT_PLAYER, rows.get(0).operation());
        assertEquals("Economy#depositPlayer(String,double)", rows.get(0).eventName());
        assertEquals("TestEconomy", rows.get(0).provider());
        assertTrue(rows.get(0).success());
        store.close();
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            if (type == String.class) {
                return "";
            }
            if (type == List.class) {
                return List.of();
            }
            return null;
        }
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0D;
        if (type == float.class) return 0.0F;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        return null;
    }
}
