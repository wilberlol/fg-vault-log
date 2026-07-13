package com.fg.vaultlog;

public enum TransactionOperation {
    DEPOSIT_PLAYER,
    WITHDRAW_PLAYER,
    BANK_DEPOSIT,
    BANK_WITHDRAW,
    BALANCE_CHANGE;

    public boolean isDeposit() {
        return this == DEPOSIT_PLAYER || this == BANK_DEPOSIT;
    }

    public boolean isPositive(double amount) {
        if (this == BALANCE_CHANGE) {
            return amount >= 0.0D;
        }
        return isDeposit();
    }
}
