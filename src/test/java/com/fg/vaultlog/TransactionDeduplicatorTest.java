package com.fg.vaultlog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TransactionDeduplicatorTest {
    @Test
    void consumesMatchingVaultAndCmiMutationOnce() {
        TransactionDeduplicator deduplicator = new TransactionDeduplicator();
        deduplicator.expect("uuid", "Wilberlol", 4.0D);

        assertTrue(deduplicator.consume("uuid", "Wilberlol", 4.0D));
        assertFalse(deduplicator.consume("uuid", "Wilberlol", 4.0D));
    }

    @Test
    void doesNotConsumeDifferentDirectionOrAmount() {
        TransactionDeduplicator deduplicator = new TransactionDeduplicator();
        deduplicator.expect("uuid", "Wilberlol", 4.0D);

        assertFalse(deduplicator.consume("uuid", "Wilberlol", -4.0D));
        assertFalse(deduplicator.consume("uuid", "Wilberlol", 5.0D));
        assertTrue(deduplicator.consume("uuid", "Wilberlol", 4.0D));
    }
}
