package com.fg.vaultlog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransactionStoreTest {
    @Test
    void writesAndQueriesAuditRows(@TempDir Path tempDirectory) throws Exception {
        Path database = tempDirectory.resolve("transactions.db");
        TransactionStore store = new TransactionStore(Logger.getLogger("FGVaultLogTest"), database.toFile());
        store.open();

        VaultTransaction first = transaction("first", "Steve", TransactionOperation.DEPOSIT_PLAYER, true, 100.0);
        VaultTransaction second = transaction("second", "Alex", TransactionOperation.WITHDRAW_PLAYER, false, 25.0);
        store.record(first);
        store.record(second);

        List<VaultTransaction> steveRows = store.queryLatest("Steve", 10, 0).get(5, TimeUnit.SECONDS);
        List<VaultTransaction> allRows = store.queryLatest(null, 10, 0).get(5, TimeUnit.SECONDS);

        assertEquals(1, steveRows.size());
        assertEquals("first", steveRows.get(0).id());
        assertEquals("Economy#depositPlayer(String,double)", steveRows.get(0).eventName());
        assertEquals(2, allRows.size());
        assertEquals("second", allRows.get(0).id());
        assertFalse(allRows.get(0).success());
        assertEquals(25.0, allRows.get(0).amount());

        store.close();
        assertTrue(database.toFile().isFile());
    }

    private static VaultTransaction transaction(String id, String account, TransactionOperation operation,
                                                boolean success, double amount) {
        return new VaultTransaction(
                id,
                Instant.now(),
                operation,
                operation == TransactionOperation.DEPOSIT_PLAYER
                        ? "Economy#depositPlayer(String,double)"
                        : "Economy#withdrawPlayer(String,double)",
                AccountType.PLAYER,
                account,
                account,
                "world",
                amount,
                amount,
                100.0,
                success ? 100.0 + amount : 100.0,
                success,
                success ? "SUCCESS" : "FAILURE",
                success ? null : "insufficient funds",
                "TestEconomy",
                "TestPlugin",
                "TestPlugin#test"
        );
    }
}
