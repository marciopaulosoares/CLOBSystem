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

    public Account(AccountId id) {
        this.id = Objects.requireNonNull(id, "AccountId cannot be null");
        this.balancesByAsset = new EnumMap<>(Asset.class);
        this.tradeHistory = new ArrayList<>();
    }

    public AccountId getId() {
        return id;
    }

    public void deposit(Asset asset, BigDecimal amount) {
        Objects.requireNonNull(asset, "Asset cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }

        balancesByAsset.computeIfAbsent(asset, a -> new Balance(BigDecimal.ZERO))
            .addAvailable(amount);
    }

    public void withdraw(Asset asset, BigDecimal amount) {
        Objects.requireNonNull(asset, "Asset cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        Balance balance = balancesByAsset.get(asset);
        if (balance == null) {
            throw new IllegalArgumentException("Insufficient available balance");
        }
        balance.subtractAvailable(amount);
    }

    void lock(Asset asset, BigDecimal amount) {
        Objects.requireNonNull(asset, "Asset cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        Balance balance = balancesByAsset.get(asset);
        if (balance == null) {
            throw new IllegalArgumentException("Insufficient available balance");
        }
        balance.lock(amount);
    }

    void unlock(Asset asset, BigDecimal amount) {
        Objects.requireNonNull(asset, "Asset cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        balancesByAsset.get(asset).unlock(amount);
    }

    void debit(Asset asset, BigDecimal amount) {
        Objects.requireNonNull(asset, "Asset cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        balancesByAsset.get(asset).debit(amount);
    }

    void credit(Asset asset, BigDecimal amount) {
        Objects.requireNonNull(asset, "Asset cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        balancesByAsset.computeIfAbsent(asset, a -> new Balance(BigDecimal.ZERO)).credit(amount);
    }

    void recordTrade(Trade trade) {
        Objects.requireNonNull(trade, "Trade cannot be null");
        tradeHistory.add(trade);
    }

    public BigDecimal getBalance(Asset asset) {
        Objects.requireNonNull(asset, "Asset cannot be null");
        return balancesByAsset.getOrDefault(asset, new Balance(BigDecimal.ZERO)).getTotal();

    }

    public BigDecimal getLockedBalance(Asset asset) {
        Objects.requireNonNull(asset, "Asset cannot be null");
        return balancesByAsset.getOrDefault(asset, new Balance(BigDecimal.ZERO)).getLocked();
    }

    public BigDecimal getAvailableBalance(Asset asset) {
        Objects.requireNonNull(asset, "Asset cannot be null");
        return balancesByAsset.getOrDefault(asset, new Balance(BigDecimal.ZERO)).getAvailable();
    }

    public List<Trade> getTradeHistory() {
        return List.copyOf(tradeHistory);
    }
}
