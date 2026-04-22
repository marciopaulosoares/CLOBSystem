package com.mb.crypto.clob.matching;

import com.mb.crypto.clob.domain.Account;
import com.mb.crypto.clob.domain.AccountId;
import com.mb.crypto.clob.domain.Asset;
import com.mb.crypto.clob.domain.Instrument;
import com.mb.crypto.clob.domain.Order;
import com.mb.crypto.clob.domain.OrderSide;
import com.mb.crypto.clob.domain.OrderStatus;
import com.mb.crypto.clob.domain.OrderType;
import com.mb.crypto.clob.domain.Scales;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency tests for {@link OrderBookEngine}.
 *
 *  1. Quantity conservation  — no BTC is created or destroyed across concurrent trades.
 *  2. Value conservation     — total BRL is conserved; money doesn't appear or vanish.
 *  3. No over-fill           — a resting order can never be filled beyond its original quantity.
 *  4. No lost trades         — N pre-matched pairs submitted concurrently all complete.
 *  5. Cancel-vs-fill race    — an order ends up either FILLED or CANCELED, never torn.
 *  6. Instrument independence — two instruments traded concurrently do not block each other.
 */
class OrderBookEngineConcurrencyTest {

    private static final Instrument BTC_BRL = new Instrument(Asset.BTC, Asset.BRL);
    private static final Instrument BRL_BTC = new Instrument(Asset.BRL, Asset.BTC);

    private static final long QTY_1 = Scales.QUANTITY_SCALE;
    private static final long PRICE  = 500L;

    private final AtomicLong idGen = new AtomicLong(1);

    // 1. Quantity conservation
    @RepeatedTest(3)
    void quantityConservation_totalBtcBoughtEqualsTotalBtcSold() throws InterruptedException {
        int pairs = 10;
        List<Account> buyers  = accounts("buyer",  pairs, new BigDecimal("50000"), BigDecimal.ZERO);
        List<Account> sellers = accounts("seller", pairs, BigDecimal.ZERO, new BigDecimal("10"));
        OrderBookEngine engine = engine(buyers, sellers);

        List<Order> buys  = new ArrayList<>();
        List<Order> sells = new ArrayList<>();
        for (int i = 0; i < pairs; i++) {
            buys .add(order(OrderSide.BUY,  PRICE, QTY_1, buyers .get(i).getId()));
            sells.add(order(OrderSide.SELL, PRICE, QTY_1, sellers.get(i).getId()));
        }

        runConcurrently(engine, buys, sells);

        BigDecimal totalBtcBought = buyers.stream()
            .map(a -> a.getBalance(Asset.BTC))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBtcSold = sellers.stream()
            .map(a -> new BigDecimal("10").subtract(a.getBalance(Asset.BTC)))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, totalBtcBought.compareTo(totalBtcSold),
            "BTC bought must equal BTC sold");
    }

    // 2. Value conservation
    @RepeatedTest(3)
    void valueConservation_totalBrlIsUnchangedAfterConcurrentTrades() throws InterruptedException {
        int pairs = 10;
        BigDecimal brlPerBuyer = new BigDecimal("50000");
        List<Account> buyers  = accounts("buyer",  pairs, brlPerBuyer, BigDecimal.ZERO);
        List<Account> sellers = accounts("seller", pairs, BigDecimal.ZERO, new BigDecimal("10"));
        OrderBookEngine engine = engine(buyers, sellers);

        BigDecimal totalBrlBefore = brlPerBuyer.multiply(BigDecimal.valueOf(pairs));

        List<Order> buys  = new ArrayList<>();
        List<Order> sells = new ArrayList<>();
        for (int i = 0; i < pairs; i++) {
            buys .add(order(OrderSide.BUY,  PRICE, QTY_1, buyers .get(i).getId()));
            sells.add(order(OrderSide.SELL, PRICE, QTY_1, sellers.get(i).getId()));
        }

        runConcurrently(engine, buys, sells);

        BigDecimal totalBrlAfter = buyers.stream()
            .map(a -> a.getBalance(Asset.BRL))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .add(sellers.stream()
                .map(a -> a.getBalance(Asset.BRL))
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        assertEquals(0, totalBrlBefore.compareTo(totalBrlAfter),
            "Total BRL must be conserved after concurrent trades");
    }

    // 3. No over-fill
    @RepeatedTest(3)
    void noOverFill_filledQuantityNeverExceedsRestingOrderQuantity() throws InterruptedException {
        int aggressors = 10;
        long restingQty = 5 * QTY_1;

        Account maker = funded("maker", BigDecimal.ZERO, new BigDecimal("100"));
        List<Account> takers = accounts("taker", aggressors, new BigDecimal("500000"), BigDecimal.ZERO);
        OrderBookEngine engine = engine(List.of(maker), takers);

        Order restingAsk = order(OrderSide.SELL, PRICE, restingQty, maker.getId());
        engine.placeOrder(restingAsk);

        List<Order> aggressorBids = new ArrayList<>();
        for (Account taker : takers) {
            aggressorBids.add(order(OrderSide.BUY, PRICE, QTY_1, taker.getId()));
        }
        runConcurrently(engine, aggressorBids);

        BigDecimal totalFilled = takers.stream()
            .map(a -> a.getBalance(Asset.BTC))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal maxFillable = BigDecimal.valueOf(restingQty, Scales.QUANTITY_DECIMALS);
        assertTrue(totalFilled.compareTo(maxFillable) <= 0,
            "Filled " + totalFilled + " must not exceed resting qty " + maxFillable);
    }

    // 4. No lost trades
    @RepeatedTest(3)
    void noLostTrades_allMatchingPairsProduceATrade() throws InterruptedException {
        int pairs = 10;
        List<Account> buyers  = accounts("buyer",  pairs, new BigDecimal("50000"), BigDecimal.ZERO);
        List<Account> sellers = accounts("seller", pairs, BigDecimal.ZERO, new BigDecimal("10"));
        OrderBookEngine engine = engine(buyers, sellers);

        List<Order> buys  = new ArrayList<>();
        List<Order> sells = new ArrayList<>();
        for (int i = 0; i < pairs; i++) {
            buys .add(order(OrderSide.BUY,  PRICE, QTY_1, buyers .get(i).getId()));
            sells.add(order(OrderSide.SELL, PRICE, QTY_1, sellers.get(i).getId()));
        }

        runConcurrently(engine, buys, sells);

        assertTrue(engine.getOrderBook(BTC_BRL).getBids().isEmpty(), "Bids must be empty");
        assertTrue(engine.getOrderBook(BTC_BRL).getAsks().isEmpty(), "Asks must be empty");

        long filled = buys.stream().filter(o -> o.getStatus() == OrderStatus.FILLED).count()
            + sells.stream().filter(o -> o.getStatus() == OrderStatus.FILLED).count();

        assertEquals(2L * pairs, filled, "Every order in a perfectly matched set must be FILLED");
    }

    // 5. Cancel-vs-fill race
    @RepeatedTest(5)
    void cancelVsFillRace_orderEndsInExactlyOneTerminalState() throws InterruptedException {
        Account maker = funded("maker", BigDecimal.ZERO, new BigDecimal("1"));
        Account taker = funded("taker", new BigDecimal("500"), BigDecimal.ZERO);
        OrderBookEngine engine = engine(List.of(maker, taker));

        Order ask = order(OrderSide.SELL, PRICE, QTY_1, maker.getId());
        engine.placeOrder(ask);
        Order bid = order(OrderSide.BUY, PRICE, QTY_1, taker.getId());

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(2);
        List<Throwable> errors = new java.util.concurrent.CopyOnWriteArrayList<>();

        Thread filler = new Thread(() -> {
            ready.countDown();
            await(start);
            try { engine.placeOrder(bid); } catch (Exception e) { errors.add(e); }
        });
        Thread canceler = new Thread(() -> {
            ready.countDown();
            await(start);
            try { engine.cancelOrder(ask); } catch (Exception ignored) {}
        });

        filler.start();
        canceler.start();
        ready.await();
        start.countDown();
        filler.join(2_000);
        canceler.join(2_000);

        assertTrue(errors.isEmpty(), "Filler must not throw: " + errors);

        OrderStatus status = ask.getStatus();
        assertTrue(status == OrderStatus.FILLED || status == OrderStatus.CANCELED,
            "Ask must be in a terminal state, was: " + status);

        if (status == OrderStatus.FILLED) {
            assertEquals(OrderStatus.FILLED, bid.getStatus(), "If ask filled, bid must be FILLED");
        } else {
            assertEquals(OrderStatus.OPEN, bid.getStatus(), "If ask canceled, bid must be OPEN");
        }
    }

    // 6. Instrument independence
    @Test
    void instrumentIndependence_concurrentTradingOnTwoInstrumentsDoesNotDeadlock()
            throws InterruptedException {
        int n = 20;

        List<Account> btcBuyers  = accounts("bb", n, new BigDecimal("500000"), BigDecimal.ZERO);
        List<Account> btcSellers = accounts("bs", n, BigDecimal.ZERO, new BigDecimal("100"));
        List<Account> brlBuyers  = accounts("rb", n, BigDecimal.ZERO, new BigDecimal("100"));
        List<Account> brlSellers = accounts("rs", n, new BigDecimal("500000"), BigDecimal.ZERO);

        Map<AccountId, Account> accountMap = new HashMap<>();
        for (List<Account> g : List.of(btcBuyers, btcSellers, brlBuyers, brlSellers)) {
            for (Account a : g) accountMap.put(a.getId(), a);
        }
        OrderBookEngine engine = new OrderBookEngine(List.of(BTC_BRL, BRL_BTC), accountMap);

        CountDownLatch ready = new CountDownLatch(4);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(4);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            pool.submit(task(ready, start, done, () -> {
                for (int i = 0; i < n; i++)
                    engine.placeOrder(orderFor(BTC_BRL, OrderSide.SELL, PRICE, QTY_1, btcSellers.get(i).getId()));
            }));
            pool.submit(task(ready, start, done, () -> {
                for (int i = 0; i < n; i++)
                    engine.placeOrder(orderFor(BTC_BRL, OrderSide.BUY, PRICE, QTY_1, btcBuyers.get(i).getId()));
            }));
            pool.submit(task(ready, start, done, () -> {
                for (int i = 0; i < n; i++)
                    engine.placeOrder(orderFor(BRL_BTC, OrderSide.SELL, PRICE, QTY_1, brlSellers.get(i).getId()));
            }));
            pool.submit(task(ready, start, done, () -> {
                for (int i = 0; i < n; i++)
                    engine.placeOrder(orderFor(BRL_BTC, OrderSide.BUY, PRICE, QTY_1, brlBuyers.get(i).getId()));
            }));

            ready.await();
            start.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS), "Possible deadlock between instruments");
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Order order(OrderSide side, long price, long qty, AccountId accountId) {
        return orderFor(BTC_BRL, side, price, qty, accountId);
    }

    private Order orderFor(Instrument instrument, OrderSide side, long price, long qty, AccountId accountId) {
        return new Order(idGen.getAndIncrement(), side, price, qty, OrderType.LIMIT, accountId, instrument);
    }

    private static Account funded(String name, BigDecimal brl, BigDecimal btc) {
        Account a = new Account(new AccountId(name));
        if (brl.compareTo(BigDecimal.ZERO) > 0) a.deposit(Asset.BRL, brl);
        if (btc.compareTo(BigDecimal.ZERO) > 0) a.deposit(Asset.BTC, btc);
        return a;
    }

    private static List<Account> accounts(String prefix, int count, BigDecimal brl, BigDecimal btc) {
        List<Account> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(funded(prefix + i, brl, btc));
        return list;
    }

    @SafeVarargs
    private OrderBookEngine engine(List<Account>... groups) {
        Map<AccountId, Account> map = new HashMap<>();
        for (List<Account> group : groups)
            for (Account a : group) map.put(a.getId(), a);
        return new OrderBookEngine(List.of(BTC_BRL), map);
    }

    @SafeVarargs
    private void runConcurrently(OrderBookEngine engine, List<Order>... orderGroups)
            throws InterruptedException {
        int total = 0;
        for (List<Order> g : orderGroups) total += g.size();

        CountDownLatch ready = new CountDownLatch(total);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(total);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (List<Order> group : orderGroups) {
                for (Order o : group) {
                    pool.submit(() -> {
                        ready.countDown();
                        await(start);
                        try { engine.placeOrder(o); } finally { done.countDown(); }
                    });
                }
            }
            ready.await();
            start.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS), "Orders did not complete within timeout");
        }
    }

    private static Runnable task(CountDownLatch ready, CountDownLatch start,
                                 CountDownLatch done, Runnable body) {
        return () -> {
            ready.countDown();
            await(start);
            try { body.run(); } finally { done.countDown(); }
        };
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
