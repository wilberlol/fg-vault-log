package com.fg.vaultlog;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Locale;

/** Suppresses the second audit row when one mutation is observed through Vault and CMI. */
public final class TransactionDeduplicator {
    private static final long TTL_MILLIS = 2_000L;
    private static final double EPSILON = 1.0E-6;

    private final ArrayDeque<Expected> expected = new ArrayDeque<>();

    public synchronized void expect(String accountId, String accountName, double delta) {
        if (!Double.isFinite(delta) || Math.abs(delta) < EPSILON) {
            return;
        }
        purgeExpired();
        expected.addLast(new Expected(normalize(accountId), normalize(accountName), delta,
                System.currentTimeMillis() + TTL_MILLIS));
    }

    public synchronized boolean consume(String accountId, String accountName, double delta) {
        if (!Double.isFinite(delta) || Math.abs(delta) < EPSILON) {
            return false;
        }
        purgeExpired();
        for (Iterator<Expected> iterator = expected.iterator(); iterator.hasNext();) {
            Expected candidate = iterator.next();
            if (sameAccount(candidate, accountId, accountName)
                    && Math.abs(candidate.delta() - delta) < EPSILON) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        expected.removeIf(item -> item.expiresAtMillis() < now);
    }

    private static boolean sameAccount(Expected expected, String accountId, String accountName) {
        String id = normalize(accountId);
        String name = normalize(accountName);
        return (!expected.accountId().isBlank() && expected.accountId().equalsIgnoreCase(id))
                || (!expected.accountName().isBlank() && expected.accountName().equalsIgnoreCase(name));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record Expected(String accountId, String accountName, double delta, long expiresAtMillis) {
    }
}
