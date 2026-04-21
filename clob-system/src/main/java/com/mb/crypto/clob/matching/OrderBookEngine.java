package com.mb.crypto.clob.matching;

import com.mb.crypto.clob.domain.Account;
import com.mb.crypto.clob.domain.AccountId;
import com.mb.crypto.clob.domain.Instrument;
import com.mb.crypto.clob.domain.Order;
import com.mb.crypto.clob.domain.OrderSide;
import com.mb.crypto.clob.domain.OrderType;
import com.mb.crypto.clob.domain.Scales;
import com.mb.crypto.clob.domain.Trade;
import com.mb.crypto.clob.orderbook.InstrumentLockRegistry;
import com.mb.crypto.clob.orderbook.OrderBook;
import com.mb.crypto.clob.validators.OrderValidator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
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
    private final OrderMatcher[] matchers;
    private final OrderValidator validator;
    private final AtomicLong tradeIdSequence = new AtomicLong(0);

    public OrderBookEngine(List<Instrument> instruments, Map<AccountId, Account> accounts) {
        Objects.requireNonNull(instruments, "Instruments cannot be null");
        Objects.requireNonNull(accounts, "Accounts cannot be null");
        this.accounts = accounts;
        this.lockRegistry = new InstrumentLockRegistry();
        this.orderBooksByInstrument = instruments.stream()
            .collect(Collectors.toMap(i -> i, OrderBook::new));
        this.matchers = new OrderMatcher[OrderType.values().length];
        this.matchers[OrderType.LIMIT.ordinal()] = new LimitOrderStrategy();
        // TODO: matchers[OrderType.MARKET.ordinal()] = new MarketOrderStrategy();
        this.validator = new OrderValidator();

    }

    @Override
    public void placeOrder(Order order) {

        validator.validate(order);

        StampedLock lock = lockRegistry.getLock(order.getInstrument());
        long stamp = lock.writeLock();
        try {
            Account account = accounts.get(order.getAccountId());
            if (order.getSide() == OrderSide.BUY) {
                account.lock(order.getInstrument().quote(), notional(order));
            } else {
                account.lock(order.getInstrument().base(),
                    BigDecimal.valueOf(order.getQuantityLong(), Scales.QUANTITY_DECIMALS));
            }

            OrderBook book = orderBooksByInstrument.get(order.getInstrument());
            if (book == null) {
                throw new IllegalArgumentException(
                    "No order book for instrument: " + order.getInstrument());
            }
            OrderMatcher matcher = matchers[order.getType().ordinal()];
            if (matcher == null) {
                throw new IllegalArgumentException(
                    "No matcher registered for order type: " + order.getType());
            }

            List<MatchedPair> matches = matcher.match(order, book, accounts);
            for (MatchedPair match : matches) {
                executeTrade(match);
            }

            if (order.getQuantityLong() > 0) {
                book.addOrder(order);
            }
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public void cancelOrder(Order order) {

        validator.validate(order);

        StampedLock lock = lockRegistry.getLock(order.getInstrument());

        long stamp = lock.writeLock();
        try {
            OrderBook orderBook = orderBooksByInstrument.get(order.getInstrument());
            if (orderBook == null) {
                throw new IllegalArgumentException(
                    "No order book for instrument: " + order.getInstrument());
            }
            orderBook.cancelOrder(order);

            Account account = accounts.get(order.getAccountId());
            if (order.getSide() == OrderSide.BUY) {
                account.unlock(order.getInstrument().quote(), notional(order));
            } else {
                account.unlock(order.getInstrument().base(),
                    BigDecimal.valueOf(order.getQuantityLong(), Scales.QUANTITY_DECIMALS));
            }
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

    private void executeTrade(MatchedPair match) {
        Order buyer  = match.incoming().getSide() == OrderSide.BUY ? match.incoming() : match.resting();
        Order seller = match.incoming().getSide() == OrderSide.BUY ? match.resting()  : match.incoming();

        BigDecimal qty     = BigDecimal.valueOf(match.qty(), Scales.QUANTITY_DECIMALS);
        BigDecimal price   = BigDecimal.valueOf(match.price());
        BigDecimal notional = price.multiply(qty);

        Trade trade = new Trade(
            tradeIdSequence.incrementAndGet(),
            buyer.getOrderId(),
            seller.getOrderId(),
            qty,
            price,
            Instant.now(),
            buyer.getAccountId(),
            seller.getAccountId()
        );

        Instrument instrument = buyer.getInstrument();

        accounts.get(seller.getAccountId())
            .settle(instrument.base(), qty, instrument.quote(), notional, trade);

        accounts.get(buyer.getAccountId())
            .settle(instrument.quote(), notional, instrument.base(), qty, trade);

        updateOrderStatus(buyer);
        updateOrderStatus(seller);
    }

    private static BigDecimal notional(Order order) {
        return BigDecimal.valueOf(order.getPriceLong())
            .multiply(BigDecimal.valueOf(order.getQuantityLong(), Scales.QUANTITY_DECIMALS));
    }

    private void updateOrderStatus(Order order) {
        order.applyFill();
    }
}
