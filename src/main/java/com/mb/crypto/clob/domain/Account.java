package com.mb.crypto.clob.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a participant account holding balances across multiple assets.
 *
 * <p>Thread-safety: balance mutation methods (lock/unlock/debit/credit/recordTrade) are
 * package-private and must only be called from within a StampedLock write section
 * held by OrderBookEngine. Public deposit/withdraw are the only externally callable
 * mutation points and must go through the matching engine for proper locking.
 */
public final class Account {

    private final AccountId id;
    private final Map<Asset, Balance> balancesByAsset;
    private final List<Trade> tradeHistory;

    /**
     * Creates an account with no initial balances.
     */
    public Account(AccountId id) {
        this.id = Objects.requireNonNull(id, "AccountId cannot be null");
        this.balancesByAsset = new EnumMap<>(Asset.class);
        this.tradeHistory = new ArrayList<>();
    }

    /**
     * Returns the account identifier.
     */
    public AccountId getId() {
        return id;
    }

    /**
     * Credits the account with the given amount of the asset (external deposit).
     */
    public void deposit(Asset asset, BigDecimal amount) {
        Objects.requireNonNull(asset, "Asset cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        // TODO: implement - get or create Balance for asset, call addAvailable(amount)
    }

    /**
     * Debits the account by the given amount of the asset (external withdrawal).
     */
    public void withdraw(Asset asset, BigDecimal amount) {
        Objects.requireNonNull(asset, "Asset cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        // TODO: implement - get Balance for asset, call subtractAvailable(amount)
    }

    void lock(Asset asset, BigDecimal amount) {
        Objects.requireNonNull(asset, "Asset cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        // TODO: implement - get Balance, call balance.lock(amount); throw if insufficient
    }

    void unlock(Asset asset, BigDecimal amount) {
        Objects.requireNonNull(asset, "Asset cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        // TODO: implement - get Balance, call balance.unlock(amount)
    }

    void debit(Asset asset, BigDecimal amount) {
        Objects.requireNonNull(asset, "Asset cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        // TODO: implement - get Balance, call balance.debit(amount)
    }

    void credit(Asset asset, BigDecimal amount) {
        Objects.requireNonNull(asset, "Asset cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        // TODO: implement - get Balance, call balance.credit(amount)
    }

    void recordTrade(Trade trade) {
        Objects.requireNonNull(trade, "Trade cannot be null");
        // TODO: implement - tradeHistory.add(trade)
    }

    /**
     * Returns the total balance (available + locked) for the given asset.
     */
    public BigDecimal getBalance(Asset asset) {
        Objects.requireNonNull(asset, "Asset cannot be null");
        // TODO: implement - return balancesByAsset.getOrDefault(asset, ZERO_BALANCE).getTotal()
        return BigDecimal.ZERO;
    }

    /**
     * Returns an unmodifiable snapshot of the trade history.
     */
    public List<Trade> getTradeHistory() {
        return List.copyOf(tradeHistory);
    }
}
