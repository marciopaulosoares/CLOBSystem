package com.mb.crypto.clob.matching;

import com.mb.crypto.clob.domain.Account;
import com.mb.crypto.clob.domain.AccountId;
import com.mb.crypto.clob.domain.Instrument;
import com.mb.crypto.clob.domain.Order;
import com.mb.crypto.clob.domain.OrderSide;
import com.mb.crypto.clob.domain.OrderType;
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
    private final Map<OrderType, OrderMatcher> matchers;
    private final OrderValidator validator;
    private final AtomicLong tradeIdSequence = new AtomicLong(0);

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
        this.validator = new OrderValidator();

    }

    @Override
    public void placeOrder(Order order) {

        validator.validate(order);

        StampedLock lock = lockRegistry.getLock(order.getInstrument());
        long stamp = lock.writeLock();
        try {
            var amountToLock = order.getPrice()
                .multiply(order.getQuantity());

            Account account = accounts.get(order.getAccountId());
            account.lock(order.getInstrument()
                .base(), amountToLock);

            OrderBook book = orderBooksByInstrument.get(order.getInstrument());
            if (book == null) {
                throw new IllegalArgumentException(
                    "No order book for instrument: " + order.getInstrument());
            }
            OrderMatcher matcher = matchers.get(order.getType());
            if (matcher == null) {
                throw new IllegalArgumentException(
                    "No matcher registered for order type: " + order.getType());
            }

            List<MatchedPair> matches = matcher.match(order, book, accounts);
            for (MatchedPair match : matches) {
                executeTrade(match);
            }

            if (order.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
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

        Trade trade = new Trade(
            tradeIdSequence.incrementAndGet(),
            buyer.getOrderId(),
            seller.getOrderId(),
            match.qty(),
            match.price(),
            Instant.now(),
            buyer.getAccountId(),
            seller.getAccountId()
        );

        Instrument instrument = buyer.getInstrument();

        accounts.get(seller.getAccountId())
            .settle(instrument.base(), match.qty(), instrument.quote(), match.qty().multiply(match.price()), trade);

        accounts.get(buyer.getAccountId())
            .settle(instrument.quote(), match.qty().multiply(match.price()), instrument.base(), match.qty(), trade);

        updateOrderStatus(buyer);
        updateOrderStatus(seller);
    }

    private void updateOrderStatus(Order order) {
        order.applyFill();
    }
}
