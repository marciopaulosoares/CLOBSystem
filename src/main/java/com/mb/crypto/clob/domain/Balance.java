package com.mb.crypto.clob.domain;

import java.math.BigDecimal;

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
        this.available = available;
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
        // TODO: implement - return available.add(locked)
        return BigDecimal.ZERO;
    }

    void addAvailable(BigDecimal amount) {
        // TODO: implement - available = available.add(amount)
    }

    void subtractAvailable(BigDecimal amount) {
        // TODO: implement - validate sufficient available,
        //  then available = available.subtract(amount)
    }

    void lock(BigDecimal amount) {
        // TODO: implement - subtractAvailable(amount); locked = locked.add(amount)
    }

    void unlock(BigDecimal amount) {
        // TODO: implement - locked = locked.subtract(amount); available = available.add(amount)
    }

    void debit(BigDecimal amount) {
        // TODO: implement - locked = locked.subtract(amount) after trade execution
    }

    void credit(BigDecimal amount) {
        // TODO: implement - available = available.add(amount) after trade execution
    }
}
