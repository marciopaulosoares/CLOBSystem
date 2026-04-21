package com.mb.crypto.clob.orderbook;

import com.mb.crypto.clob.domain.Instrument;
import com.mb.crypto.clob.domain.Order;
import com.mb.crypto.clob.domain.OrderSide;
import com.mb.crypto.clob.domain.OrderStatus;

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

    public Instrument getInstrument() {
        return instrument;
    }
    public NavigableMap<BigDecimal, ArrayDeque<Order>> getBids() {
        return Collections.unmodifiableNavigableMap(bids);
    }
    public NavigableMap<BigDecimal, ArrayDeque<Order>> getAsks() {
        return Collections.unmodifiableNavigableMap(asks);
    }

    public OrderBook(Instrument instrument) {
        this.instrument = Objects.requireNonNull(instrument, "Instrument cannot be null");
        this.bids = new TreeMap<>(Comparator.reverseOrder());
        this.asks = new TreeMap<>();
    }

    public void addOrder(Order order) {

        NavigableMap<BigDecimal, ArrayDeque<Order>> side =
            order.getSide() == OrderSide.BUY ? bids : asks;

        side.computeIfAbsent(order.getPrice(), k -> new ArrayDeque<>()).add(order);
    }

    public void purgeEmptyLevel(BigDecimal price, OrderSide side) {
        NavigableMap<BigDecimal, ArrayDeque<Order>> map = side == OrderSide.BUY ? bids : asks;
        ArrayDeque<Order> queue = map.get(price);
        if (queue != null && queue.isEmpty()) {
            map.remove(price);
        }
    }

    public void cancelOrder(Order order) {
        Objects.requireNonNull(order, "Order cannot be null");
        OrderStatus status = order.getStatus();
        if (status != OrderStatus.OPEN && status != OrderStatus.PARTIALLY_FILLED) {
            throw new IllegalStateException(
                "Order " + order.getOrderId() + " is not active: " + status);
        }
        NavigableMap<BigDecimal, ArrayDeque<Order>> side =
            order.getSide() == OrderSide.BUY ? bids : asks;
        ArrayDeque<Order> queue = side.get(order.getPrice());
        if (queue == null || !queue.remove(order)) {
            throw new IllegalArgumentException(
                "Order not found in book: " + order.getOrderId());
        }
        if (queue.isEmpty()) {
            side.remove(order.getPrice());
        }
        order.cancel();
    }
}
