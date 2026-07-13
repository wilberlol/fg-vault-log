package com.fg.vaultlog;

import java.time.Instant;
import java.util.Objects;

/** Immutable audit row shared by the Economy proxy and SQLite store. */
public record VaultTransaction(
        String id,
        Instant occurredAt,
        TransactionOperation operation,
        AccountType accountType,
        String accountId,
        String accountName,
        String world,
        double requestedAmount,
        double amount,
        Double balanceBefore,
        Double balanceAfter,
        boolean success,
        String responseType,
        String message,
        String provider,
        String sourcePlugin,
        String sourceClass
) {
    public VaultTransaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(accountType, "accountType");
        accountId = Objects.requireNonNullElse(accountId, "");
        responseType = Objects.requireNonNullElse(responseType, "UNKNOWN");
        provider = Objects.requireNonNullElse(provider, "unknown");
        sourcePlugin = Objects.requireNonNullElse(sourcePlugin, "unknown");
        sourceClass = Objects.requireNonNullElse(sourceClass, "unknown");
    }

    public String accountLabel() {
        if (accountName != null && !accountName.isBlank()) {
            return accountName;
        }
        return accountId;
    }
}
