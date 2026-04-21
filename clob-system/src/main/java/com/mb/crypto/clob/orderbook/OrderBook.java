package com.mb.crypto.clob.orderbook;

import com.mb.crypto.clob.domain.Instrument;
import com.mb.crypto.clob.domain.Order;
import com.mb.crypto.clob.domain.OrderSide;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Price-level order book for a single instrument.
 *
 * <p>Bids are stored in descending price order (highest bid = best bid = firstKey()).
 * Asks are stored in ascending price order (lowest ask = best ask = firstKey()).
 * Price keys are stored as long (integer BRL, Scales.PRICE_DECIMALS = 0).
 *
 * <p>Not thread-safe by itself: all access must be guarded by the StampedLock
 * obtained from InstrumentLockRegistry for the corresponding instrument.
 */
public final class OrderBook {

    private final Instrument instrument;

    private final NavigableMap<Long, ArrayDeque<Order>> bids;
    private final NavigableMap<Long, ArrayDeque<Order>> asks;
    private final Map<Long, Order> ordersById = new HashMap<>();

    private final Map<Long, ArrayDeque<Order>> queueByOrderId = new HashMap<>();

    public OrderBook(Instrument instrument) {
        this.instrument = Objects.requireNonNull(instrument);
        this.bids = new TreeMap<>(Comparator.reverseOrder());
        this.asks = new TreeMap<>();
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public NavigableMap<Long, ArrayDeque<Order>> getBids() {
        return Collections.unmodifiableNavigableMap(bids);
    }

    public NavigableMap<Long, ArrayDeque<Order>> getAsks() {
        return Collections.unmodifiableNavigableMap(asks);
    }

    public void addOrder(Order order) {
        Objects.requireNonNull(order);

        NavigableMap<Long, ArrayDeque<Order>> side =
            order.getSide() == OrderSide.BUY ? bids : asks;

        long price = order.getPriceLong();

        ArrayDeque<Order> queue = side.get(price);
        if (queue == null) {
            queue = new ArrayDeque<>();
            side.put(price, queue);
        }

        queue.add(order);

        ordersById.put(order.getOrderId(), order);
        queueByOrderId.put(order.getOrderId(), queue);
    }

    public void cancelOrder(Order order) {
        Objects.requireNonNull(order);

        Order stored = ordersById.remove(order.getOrderId());
        ArrayDeque<Order> queue = queueByOrderId.remove(order.getOrderId());

        if (stored == null || queue == null) {
            throw new IllegalArgumentException(
                "Order not found: " + order.getOrderId());
        }

        // still O(n), but only within one price level (small)
        boolean removed = queue.remove(stored);

        if (!removed) {
            throw new IllegalStateException(
                "Order not found in its queue: " + order.getOrderId());
        }

        if (queue.isEmpty()) {
            long price = stored.getPriceLong();
            NavigableMap<Long, ArrayDeque<Order>> side =
                stored.getSide() == OrderSide.BUY ? bids : asks;

            side.remove(price);
        }

        stored.cancel();
    }

    public void purgeEmptyLevel(long price, OrderSide side) {
        NavigableMap<Long, ArrayDeque<Order>> map =
            side == OrderSide.BUY ? bids : asks;

        ArrayDeque<Order> queue = map.get(price);
        if (queue != null && queue.isEmpty()) {
            map.remove(price);
        }
    }
}