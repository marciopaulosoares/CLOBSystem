package com.mb.crypto.clob.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Tracks available and locked (reserved) quantities of a single asset for an account.
 * Mutable by design: lock/unlock/debit/credit operations update state in place.
 * All mutations must be called from within a StampedLock write section in OrderBookEngine.
 */
public final class Balance {

    private BigDecimal available;
    private BigDecimal locked;

    /**
     * Initialises balance with the given available amount and zero locked funds.
     */
    public Balance(BigDecimal available) {
        this.available = Objects.requireNonNull(available, "Available cannot be null");
        this.locked = BigDecimal.ZERO;
    }

    /**
     * Returns the currently available (unreserved) amount.
     */
    public BigDecimal getAvailable() {
        return available;
    }

    /**
     * Returns the currently locked (reserved) amount.
     */
    public BigDecimal getLocked() {
        return locked;
    }

    /**
     * Returns the total balance (available plus locked).
     */
    public BigDecimal getTotal() {
        return available.add(locked);
    }

    void addAvailable(BigDecimal amount) {
        available = available.add(amount);
    }

    void subtractAvailable(BigDecimal amount) {
        if (available.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient available balance");
        }
        available = available.subtract(amount);
    }

    void lock(BigDecimal amount) {
        subtractAvailable(amount);
        locked = locked.add(amount);
    }

    void unlock(BigDecimal amount) {
        locked = locked.subtract(amount);
        available = available.add(amount);
    }

    void debit(BigDecimal amount) {
        locked = locked.subtract(amount);
    }

    void credit(BigDecimal amount) {
        available = available.add(amount);
    }
}
