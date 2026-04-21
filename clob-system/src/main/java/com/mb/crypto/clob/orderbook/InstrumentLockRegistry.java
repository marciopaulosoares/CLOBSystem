package com.mb.crypto.clob.orderbook;

import com.mb.crypto.clob.domain.Instrument;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;

/**
 * Manages one {@link StampedLock} per instrument to provide fine-grained,
 * per-instrument concurrency control across the order book engine.
 *
 * <p>Locks are lazily created on first access and are never removed, making
 * {@link #getLock(Instrument)} safe to call from any thread without additional
 * synchronization. All order placement, cancellation, and matching operations
 * must acquire the appropriate lock before touching an instrument's OrderBook.
 */
public final class InstrumentLockRegistry {

    private final Map<Instrument, StampedLock> locks;

    /**
     * Creates an empty lock registry.
     */
    public InstrumentLockRegistry() {
        this.locks = new ConcurrentHashMap<>();
    }

    /**
     * Returns the {@link StampedLock} for the given instrument, creating it if absent.
     */
    public StampedLock getLock(Instrument instrument) {
        Objects.requireNonNull(instrument, "Instrument cannot be null");
        return locks.computeIfAbsent(instrument, k -> new StampedLock());
    }
}
