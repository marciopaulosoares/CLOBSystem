package com.mb.crypto.clob.matching;

import com.mb.crypto.clob.domain.Account;
import com.mb.crypto.clob.domain.AccountId;
import com.mb.crypto.clob.domain.Instrument;
import com.mb.crypto.clob.domain.Order;
import com.mb.crypto.clob.domain.OrderType;
import com.mb.crypto.clob.orderbook.InstrumentLockRegistry;
import com.mb.crypto.clob.orderbook.OrderBook;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.StampedLock;
import java.util.stream.Collectors;

/**
 * Primary implementation of {@link MatchingEngine}.
 *
 * <p>Maintains one {@link OrderBook} per instrument and routes incoming orders to the
 * appropriate {@link OrderMatcher} via the Strategy Pattern keyed on {@link OrderType}.
 * All mutating operations (placeOrder, cancelOrder) acquire a StampedLock write stamp
 * for the target instrument. Read queries (getOrderBook) use an optimistic read with
 * fallback to a full read lock to minimize contention under high read load.
 */
public final class OrderBookEngine implements MatchingEngine {

    private final Map<Instrument, OrderBook> orderBooksByInstrument;
    private final Map<AccountId, Account> accounts;
    private final InstrumentLockRegistry lockRegistry;
    private final Map<OrderType, OrderMatcher> matchers;

    /**
     * Initialises the engine with one order book per instrument and registers matchers.
     */
    public OrderBookEngine(List<Instrument> instruments, Map<AccountId, Account> accounts) {
        Objects.requireNonNull(instruments, "Instruments cannot be null");
        Objects.requireNonNull(accounts, "Accounts cannot be null");
        this.accounts = accounts;
        this.lockRegistry = new InstrumentLockRegistry();
        this.orderBooksByInstrument = instruments.stream()
            .collect(Collectors.toMap(i -> i, OrderBook::new));
        this.matchers = Map.of(
            OrderType.LIMIT, new LimitOrderStrategy()
        // TODO: add OrderType.MARKET -> new MarketOrderStrategy() when implemented
        );
    }

    @Override
    public void placeOrder(Order order) {
        Objects.requireNonNull(order, "Order cannot be null");
        OrderBook orderBook = orderBooksByInstrument.get(order.getInstrument());
        if (orderBook == null) {
            throw new IllegalArgumentException(
                "No order book for instrument: " + order.getInstrument());
        }
        OrderMatcher matcher = matchers.get(order.getType());
        if (matcher == null) {
            throw new IllegalArgumentException(
                "No matcher registered for order type: " + order.getType());
        }
        StampedLock lock = lockRegistry.getLock(order.getInstrument());
        long stamp = lock.writeLock();
        try {
            orderBook.addOrder(order);
            matcher.match(order, orderBook, accounts);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public void cancelOrder(long orderId, Instrument instrument) {
        Objects.requireNonNull(instrument, "Instrument cannot be null");
        StampedLock lock = lockRegistry.getLock(instrument);
        long stamp = lock.writeLock();
        try {
            // TODO: implement - look up Order by orderId, call orderBook.cancelOrder(order),
            //   update order status to CANCELED, unlock reserved balance on account
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public OrderBook getOrderBook(Instrument instrument) {
        Objects.requireNonNull(instrument, "Instrument cannot be null");
        StampedLock lock = lockRegistry.getLock(instrument);
        long stamp = lock.tryOptimisticRead();
        OrderBook result = orderBooksByInstrument.get(instrument);
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                result = orderBooksByInstrument.get(instrument);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return result;
    }

    private void executeTrade(Order incoming, Order resting) {
        // TODO: implement trade execution:
        //   1. Determine filled quantity = min(incoming.getQuantity(), resting.getQuantity())
        //   2. Determine execution price = resting.getPrice() (price-time priority)
        //   3. Build Trade record
        //   4. Debit seller's base asset locked balance; credit buyer's base asset available
        //   5. Debit buyer's quote asset locked balance; credit seller's quote asset available
        //   6. Call account.recordTrade(trade) for both buyer and seller
        //   7. Update order quantities and statuses (FILLED / partially remaining)
    }
}
