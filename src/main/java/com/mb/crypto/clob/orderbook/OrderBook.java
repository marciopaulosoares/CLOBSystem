package com.mb.crypto.clob.orderbook;

import com.mb.crypto.clob.domain.Instrument;
import com.mb.crypto.clob.domain.Order;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Price-level order book for a single instrument.
 *
 * <p>Bids are stored in descending price order (highest bid = best bid = firstKey()).
 * Asks are stored in ascending price order (lowest ask = best ask = firstKey()).
 *
 * <p>Not thread-safe by itself: all access must be guarded by the StampedLock
 * obtained from InstrumentLockRegistry for the corresponding instrument.
 */
public final class OrderBook {

    private final Instrument instrument;
    private final NavigableMap<BigDecimal, ArrayDeque<Order>> bids;
    private final NavigableMap<BigDecimal, ArrayDeque<Order>> asks;

    /**
     * Creates an empty order book for the given instrument.
     */
    public OrderBook(Instrument instrument) {
        this.instrument = Objects.requireNonNull(instrument, "Instrument cannot be null");
        this.bids = new TreeMap<>(Comparator.reverseOrder());
        this.asks = new TreeMap<>();
    }

    /**
     * Returns the instrument this order book tracks.
     */
    public Instrument getInstrument() {
        return instrument;
    }

    /**
     * Returns an unmodifiable view of the bid side (descending price order).
     */
    public NavigableMap<BigDecimal, ArrayDeque<Order>> getBids() {
        return Collections.unmodifiableNavigableMap(bids);
    }

    /**
     * Returns an unmodifiable view of the ask side (ascending price order).
     */
    public NavigableMap<BigDecimal, ArrayDeque<Order>> getAsks() {
        return Collections.unmodifiableNavigableMap(asks);
    }

    /**
     * Adds the order to the appropriate side of the book at its limit price.
     */
    public void addOrder(Order order) {
        Objects.requireNonNull(order, "Order cannot be null");
        // TODO: implement - route to bids or asks based on order.getSide(),
        //   computeIfAbsent(price, k -> new ArrayDeque<>()).add(order)
    }

    /**
     * Removes the order from the book; removes the price level if the queue becomes empty.
     */
    public void cancelOrder(Order order) {
        Objects.requireNonNull(order, "Order cannot be null");
        // TODO: implement - remove order from the correct side's deque;
        //   remove the price-level entry if the deque becomes empty
    }
}
