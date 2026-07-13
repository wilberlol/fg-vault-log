package com.fg.vaultlog;

public enum TransactionOperation {
    DEPOSIT_PLAYER,
    WITHDRAW_PLAYER,
    BANK_DEPOSIT,
    BANK_WITHDRAW;

    public boolean isDeposit() {
        return this == DEPOSIT_PLAYER || this == BANK_DEPOSIT;
    }
}
