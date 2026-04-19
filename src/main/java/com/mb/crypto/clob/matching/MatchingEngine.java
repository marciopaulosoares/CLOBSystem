package com.mb.crypto.clob.matching;

import com.mb.crypto.clob.domain.Instrument;
import com.mb.crypto.clob.domain.Order;
import com.mb.crypto.clob.orderbook.OrderBook;

/**
 * Contract for the central matching engine.
 * Implementations must be thread-safe; each instrument is guarded by its own StampedLock.
 */
public interface MatchingEngine {

    void placeOrder(Order order);

    void cancelOrder(long orderId, Instrument instrument);

    OrderBook getOrderBook(Instrument instrument);
}
