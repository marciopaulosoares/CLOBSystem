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
import com.mb.crypto.clob.orderbook.OrderBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookEngineTest {

    private static final Instrument BTC_BRL   = new Instrument(Asset.BTC, Asset.BRL);
    private static final AccountId  BUYER_ID  = new AccountId("buyer");
    private static final AccountId  SELLER_ID = new AccountId("seller");

    private static final long QTY_1 = 1 * Scales.QUANTITY_SCALE;   // 1 BTC  in satoshis
    private static final long QTY_2 = 2 * Scales.QUANTITY_SCALE;   // 2 BTC  in satoshis
    private static final long QTY_3 = 3 * Scales.QUANTITY_SCALE;

    private final AtomicLong ids = new AtomicLong(1);

    private Account buyer;
    private Account seller;
    private OrderBookEngine engine;

    @BeforeEach
    void setUp() {
        buyer  = funded(BUYER_ID,  new BigDecimal("10000"), BigDecimal.ZERO);
        seller = funded(SELLER_ID, BigDecimal.ZERO,         new BigDecimal("10"));
        engine = engine(List.of(buyer, seller));
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    @Nested
    class Constructor {

        @Test
        void shouldRejectNullInstruments() {
            assertThrows(NullPointerException.class,
                () -> new OrderBookEngine(null, Map.of()));
        }

        @Test
        void shouldRejectNullAccounts() {
            assertThrows(NullPointerException.class,
                () -> new OrderBookEngine(List.of(BTC_BRL), null));
        }
    }

    // -----------------------------------------------------------------------
    // placeOrder — null / validation
    // -----------------------------------------------------------------------

    @Nested
    class PlaceOrderValidation {

        @Test
        void shouldRejectNullOrder() {
            assertThrows(NullPointerException.class, () -> engine.placeOrder(null));
        }

        @Test
        void shouldRejectOrderForUnknownInstrument() {
            // engine only knows BTC_BRL; a BRL_BTC order has no matching book
            Account extra = funded(new AccountId("extra"), new BigDecimal("5000"), BigDecimal.ZERO);
            OrderBookEngine eng = new OrderBookEngine(
                List.of(new Instrument(Asset.BRL, Asset.BTC)), accountMap(List.of(extra)));

            // order instrument = BTC_BRL, which the engine above does not know
            Order order = new Order(ids.getAndIncrement(), OrderSide.BUY, 500, QTY_1,
                OrderType.LIMIT, extra.getId(), BTC_BRL);
            assertThrows(IllegalArgumentException.class, () -> eng.placeOrder(order));
        }

        @Test
        void shouldRejectCanceledOrder() {
            Order order = buy(500, QTY_1);
            order.cancel();
            assertThrows(IllegalStateException.class, () -> engine.placeOrder(order));
        }

        @Test
        void shouldRejectFilledOrder() {
            // place a matching pair so the order reaches FILLED status via the engine
            Order ask = sell(500, QTY_1);
            Order bid = buy(500, QTY_1);
            engine.placeOrder(ask);
            engine.placeOrder(bid);

            assertEquals(OrderStatus.FILLED, bid.getStatus());
            assertThrows(IllegalStateException.class, () -> engine.placeOrder(bid));
        }
    }

    // -----------------------------------------------------------------------
    // placeOrder — balance locking
    // -----------------------------------------------------------------------

    @Nested
    class BalanceLocking {

        @Test
        void buyOrder_locksQuoteNotionalBeforeMatch() {
            // notional = price × qty = 500 × 1 BTC = 500 BRL
            BigDecimal initialBrl = buyer.getAvailableBalance(Asset.BRL);
            engine.placeOrder(buy(500, QTY_1));

            // no ask in book, so order rests — locked notional must reflect
            BigDecimal expectedLocked = new BigDecimal("500.00000000");
            assertEquals(expectedLocked, buyer.getLockedBalance(Asset.BRL));
            assertEquals(initialBrl.subtract(expectedLocked), buyer.getAvailableBalance(Asset.BRL));
        }

        @Test
        void sellOrder_locksBaseQuantityBeforeMatch() {
            BigDecimal initialBtc = seller.getAvailableBalance(Asset.BTC);
            engine.placeOrder(sell(500, QTY_1));

            BigDecimal expectedLocked = BigDecimal.valueOf(QTY_1, Scales.QUANTITY_DECIMALS); // 1.00000000
            assertEquals(expectedLocked, seller.getLockedBalance(Asset.BTC));
            assertEquals(initialBtc.subtract(expectedLocked), seller.getAvailableBalance(Asset.BTC));
        }

        @Test
        void buyOrder_insufficientBalance_throwsBeforeBookIsModified() {
            Account poorBuyer = funded(new AccountId("poor"), new BigDecimal("1"), BigDecimal.ZERO);
            OrderBookEngine eng = engine(List.of(poorBuyer, seller));

            // notional = 500 × 1 BTC = 500 BRL, but only 1 BRL available
            Order order = new Order(ids.getAndIncrement(), OrderSide.BUY, 500, QTY_1,
                OrderType.LIMIT, poorBuyer.getId(), BTC_BRL);

            assertThrows(IllegalArgumentException.class, () -> eng.placeOrder(order));
            assertTrue(eng.getOrderBook(BTC_BRL).getBids().isEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // placeOrder — no match (order rests in book)
    // -----------------------------------------------------------------------

    @Nested
    class NoMatch {

        @Test
        void buyBelowAsk_orderAddsToBook() {
            engine.placeOrder(sell(500, QTY_1));
            engine.placeOrder(buy(499, QTY_1));   // bid below ask — no match

            assertFalse(engine.getOrderBook(BTC_BRL).getBids().isEmpty());
        }

        @Test
        void buyBelowAsk_statusRemainsOpen() {
            engine.placeOrder(sell(500, QTY_1));
            Order bid = buy(499, QTY_1);
            engine.placeOrder(bid);

            assertEquals(OrderStatus.OPEN, bid.getStatus());
        }

        @Test
        void sellAboveBid_orderAddsToBook() {
            engine.placeOrder(buy(500, QTY_1));
            engine.placeOrder(sell(501, QTY_1));  // ask above bid — no match

            assertFalse(engine.getOrderBook(BTC_BRL).getAsks().isEmpty());
        }

        @Test
        void emptyBook_orderRestingAtCorrectPriceLevel() {
            engine.placeOrder(buy(500, QTY_1));

            assertTrue(engine.getOrderBook(BTC_BRL).getBids().containsKey(500L));
        }
    }

    // -----------------------------------------------------------------------
    // placeOrder — full fill
    // -----------------------------------------------------------------------

    @Nested
    class FullFill {

        @Test
        void matchingOrders_bothStatusFilled() {
            Order ask = sell(500, QTY_1);
            Order bid = buy(500, QTY_1);
            engine.placeOrder(ask);
            engine.placeOrder(bid);

            assertEquals(OrderStatus.FILLED, ask.getStatus());
            assertEquals(OrderStatus.FILLED, bid.getStatus());
        }

        @Test
        void matchingOrders_bookRemainsEmpty() {
            engine.placeOrder(sell(500, QTY_1));
            engine.placeOrder(buy(500, QTY_1));

            assertTrue(engine.getOrderBook(BTC_BRL).getAsks().isEmpty());
            assertTrue(engine.getOrderBook(BTC_BRL).getBids().isEmpty());
        }

        @Test
        void matchingOrders_sellerReceivesBrl() {
            engine.placeOrder(sell(500, QTY_1));
            engine.placeOrder(buy(500, QTY_1));

            // seller delivered 1 BTC and received 500 BRL
            assertEquals(new BigDecimal("500.00000000"), seller.getBalance(Asset.BRL));
        }

        @Test
        void matchingOrders_buyerReceivesBtc() {
            engine.placeOrder(sell(500, QTY_1));
            engine.placeOrder(buy(500, QTY_1));

            // buyer delivered 500 BRL and received 1 BTC
            assertEquals(BigDecimal.valueOf(QTY_1, Scales.QUANTITY_DECIMALS),
                buyer.getBalance(Asset.BTC));
        }

        @Test
        void matchingOrders_buyerBrlBalanceLockFullyConsumed() {
            engine.placeOrder(sell(500, QTY_1));
            engine.placeOrder(buy(500, QTY_1));

            assertEquals(0, buyer.getLockedBalance(Asset.BRL).compareTo(BigDecimal.ZERO));
        }

        @Test
        void matchingOrders_sellerBtcBalanceLockFullyConsumed() {
            engine.placeOrder(sell(500, QTY_1));
            engine.placeOrder(buy(500, QTY_1));

            assertEquals(0, seller.getLockedBalance(Asset.BTC).compareTo(BigDecimal.ZERO));
        }

        @Test
        void matchingOrders_tradeRecordedInBothAccounts() {
            engine.placeOrder(sell(500, QTY_1));
            engine.placeOrder(buy(500, QTY_1));

            assertEquals(1, buyer.getTradeHistory().size());
            assertEquals(1, seller.getTradeHistory().size());
        }

        @Test
        void buyAboveAsk_matchesAtAskPrice() {
            // resting ask at 490, incoming bid at 500 — trade executes at 490
            Account richBuyer = funded(new AccountId("rich"), new BigDecimal("10000"), BigDecimal.ZERO);
            Account cheapSeller = funded(new AccountId("cheap"), BigDecimal.ZERO, new BigDecimal("1"));
            OrderBookEngine eng = engine(List.of(richBuyer, cheapSeller));

            Order ask = order(OrderSide.SELL, 490, QTY_1, cheapSeller.getId());
            Order bid = order(OrderSide.BUY,  500, QTY_1, richBuyer.getId());
            eng.placeOrder(ask);
            eng.placeOrder(bid);

            // seller receives 490, not 500
            assertEquals(new BigDecimal("490.00000000"), cheapSeller.getBalance(Asset.BRL));
        }
    }

    // -----------------------------------------------------------------------
    // placeOrder — partial fill
    // -----------------------------------------------------------------------

    @Nested
    class PartialFill {

        @Test
        void aggressorSmallerThanResting_aggressorFullyFilled() {
            Order ask = sell(500, QTY_2);  // 2 BTC resting
            Order bid = buy(500, QTY_1);   // 1 BTC incoming
            engine.placeOrder(ask);
            engine.placeOrder(bid);

            assertEquals(OrderStatus.FILLED, bid.getStatus());
        }

        @Test
        void aggressorSmallerThanResting_restingPartiallyFilled() {
            Order ask = sell(500, QTY_2);
            Order bid = buy(500, QTY_1);
            engine.placeOrder(ask);
            engine.placeOrder(bid);

            assertEquals(OrderStatus.PARTIALLY_FILLED, ask.getStatus());
        }

        @Test
        void aggressorSmallerThanResting_remainderStaysInBook() {
            engine.placeOrder(sell(500, QTY_2));
            engine.placeOrder(buy(500, QTY_1));

            assertTrue(engine.getOrderBook(BTC_BRL).getAsks().containsKey(500L));
        }

        @Test
        void aggressorLargerThanResting_aggressorPartiallyFilled() {
            Order ask = sell(500, QTY_1);  // 1 BTC resting
            Order bid = buy(500, QTY_2);   // 2 BTC incoming
            engine.placeOrder(ask);
            engine.placeOrder(bid);

            assertEquals(OrderStatus.PARTIALLY_FILLED, bid.getStatus());
        }

        @Test
        void aggressorLargerThanResting_remainderAddedToBook() {
            engine.placeOrder(sell(500, QTY_1));
            engine.placeOrder(buy(500, QTY_2));

            assertTrue(engine.getOrderBook(BTC_BRL).getBids().containsKey(500L));
        }

        @Test
        void partialFill_settledOnlyForTradedQuantity() {
            engine.placeOrder(sell(500, QTY_2));  // 2 BTC resting
            engine.placeOrder(buy(500, QTY_1));   // 1 BTC consumed

            // seller gets paid only for 1 BTC (500 BRL), 1 BTC still locked
            assertEquals(new BigDecimal("500.00000000"), seller.getBalance(Asset.BRL));
            assertEquals(BigDecimal.valueOf(QTY_1, Scales.QUANTITY_DECIMALS),
                seller.getLockedBalance(Asset.BTC));
        }
    }

    // -----------------------------------------------------------------------
    // placeOrder — sweep multiple resting levels
    // -----------------------------------------------------------------------

    @Nested
    class MultiLevelSweep {

        @Test
        void aggressorSweepsTwoLevels_allRestingOrdersFilled() {
            Account seller2 = funded(new AccountId("seller2"), BigDecimal.ZERO, new BigDecimal("10"));
            OrderBookEngine eng = engine(List.of(buyer, seller, seller2));

            Order ask1 = order(OrderSide.SELL, 490, QTY_1, SELLER_ID);
            Order ask2 = order(OrderSide.SELL, 495, QTY_1, seller2.getId());
            Order bid  = order(OrderSide.BUY,  500, QTY_2, BUYER_ID);

            eng.placeOrder(ask1);
            eng.placeOrder(ask2);
            eng.placeOrder(bid);

            assertEquals(OrderStatus.FILLED, ask1.getStatus());
            assertEquals(OrderStatus.FILLED, ask2.getStatus());
            assertEquals(OrderStatus.FILLED, bid.getStatus());
        }

        @Test
        void aggressorSweepsTwoLevels_bookIsEmpty() {
            Account seller2 = funded(new AccountId("seller2"), BigDecimal.ZERO, new BigDecimal("10"));
            OrderBookEngine eng = engine(List.of(buyer, seller, seller2));

            eng.placeOrder(order(OrderSide.SELL, 490, QTY_1, SELLER_ID));
            eng.placeOrder(order(OrderSide.SELL, 495, QTY_1, seller2.getId()));
            eng.placeOrder(order(OrderSide.BUY,  500, QTY_2, BUYER_ID));

            assertTrue(eng.getOrderBook(BTC_BRL).getAsks().isEmpty());
        }

        @Test
        void aggressorSweepsTwoLevels_twoTradesRecordedForBuyer() {
            Account seller2 = funded(new AccountId("seller2"), BigDecimal.ZERO, new BigDecimal("10"));
            OrderBookEngine eng = engine(List.of(buyer, seller, seller2));

            eng.placeOrder(order(OrderSide.SELL, 490, QTY_1, SELLER_ID));
            eng.placeOrder(order(OrderSide.SELL, 495, QTY_1, seller2.getId()));
            eng.placeOrder(order(OrderSide.BUY,  500, QTY_2, BUYER_ID));

            assertEquals(2, buyer.getTradeHistory().size());
        }
    }

    // -----------------------------------------------------------------------
    // cancelOrder
    // -----------------------------------------------------------------------

    @Nested
    class CancelOrder {

        @Test
        void shouldRejectNullOrder() {
            assertThrows(NullPointerException.class, () -> engine.cancelOrder(null));
        }

        @Test
        void shouldRejectOrderNotInBook() {
            Order ask = sell(500, QTY_1);
            engine.placeOrder(ask);           // places and rests in book
            engine.cancelOrder(ask);          // remove it
            assertThrows(Exception.class, () -> engine.cancelOrder(ask)); // already gone
        }

        @Test
        void cancelBuyOrder_statusBecomesCancel() {
            Order bid = buy(499, QTY_1);      // no ask, rests in book
            engine.placeOrder(bid);
            engine.cancelOrder(bid);

            assertEquals(OrderStatus.CANCELED, bid.getStatus());
        }

        @Test
        void cancelBuyOrder_removedFromBook() {
            Order bid = buy(499, QTY_1);
            engine.placeOrder(bid);
            engine.cancelOrder(bid);

            assertTrue(engine.getOrderBook(BTC_BRL).getBids().isEmpty());
        }

        @Test
        void cancelBuyOrder_unlocksQuoteBalance() {
            Order bid = buy(500, QTY_1);
            engine.placeOrder(bid);

            BigDecimal lockedBefore = buyer.getLockedBalance(Asset.BRL);
            assertTrue(lockedBefore.compareTo(BigDecimal.ZERO) > 0);

            engine.cancelOrder(bid);
            assertEquals(0, buyer.getLockedBalance(Asset.BRL).compareTo(BigDecimal.ZERO));
        }

        @Test
        void cancelSellOrder_statusBecomesCancel() {
            Order ask = sell(600, QTY_1);     // no bid, rests in book
            engine.placeOrder(ask);
            engine.cancelOrder(ask);

            assertEquals(OrderStatus.CANCELED, ask.getStatus());
        }

        @Test
        void cancelSellOrder_removedFromBook() {
            Order ask = sell(600, QTY_1);
            engine.placeOrder(ask);
            engine.cancelOrder(ask);

            assertTrue(engine.getOrderBook(BTC_BRL).getAsks().isEmpty());
        }

        @Test
        void cancelSellOrder_unlocksBaseBalance() {
            Order ask = sell(500, QTY_1);
            engine.placeOrder(ask);

            BigDecimal lockedBefore = seller.getLockedBalance(Asset.BTC);
            assertTrue(lockedBefore.compareTo(BigDecimal.ZERO) > 0);

            engine.cancelOrder(ask);
            assertEquals(0, seller.getLockedBalance(Asset.BTC).compareTo(BigDecimal.ZERO));
        }

        @Test
        void shouldRejectAlreadyCanceledOrder() {
            Order ask = sell(500, QTY_1);
            engine.placeOrder(ask);
            engine.cancelOrder(ask);
            assertThrows(IllegalStateException.class, () -> engine.cancelOrder(ask));
        }
    }

    // -----------------------------------------------------------------------
    // getOrderBook
    // -----------------------------------------------------------------------

    @Nested
    class GetOrderBook {

        @Test
        void shouldRejectNullInstrument() {
            assertThrows(NullPointerException.class, () -> engine.getOrderBook(null));
        }

        @Test
        void unknownInstrument_returnsNull() {
            Instrument other = new Instrument(Asset.BRL, Asset.BTC);
            assertNull(engine.getOrderBook(other));
        }

        @Test
        void knownInstrument_returnsNonNullBook() {
            assertNotNull(engine.getOrderBook(BTC_BRL));
        }

        @Test
        void reflectsOrdersPlacedInBook() {
            engine.placeOrder(sell(500, QTY_1));
            OrderBook book = engine.getOrderBook(BTC_BRL);

            assertTrue(book.getAsks().containsKey(500L));
        }

        @Test
        void bookIsEmptyBeforeAnyOrder() {
            OrderBook book = engine.getOrderBook(BTC_BRL);

            assertTrue(book.getBids().isEmpty());
            assertTrue(book.getAsks().isEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // Concurrency
    // -----------------------------------------------------------------------

    @Nested
    class Concurrency {

        @Test
        void concurrentBuysAndSells_noLostUpdatesAndConsistentBookState() throws InterruptedException {
            int threads   = 8;
            int perThread = 20;

            List<Account> accounts = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                accounts.add(funded(new AccountId("t" + i),
                    new BigDecimal("10000000"), new BigDecimal("1000")));
            }
            OrderBookEngine eng = engine(accounts);

            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done  = new CountDownLatch(threads);
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            AtomicLong idGen     = new AtomicLong(1000);

            for (int t = 0; t < threads; t++) {
                AccountId aid = accounts.get(t).getId();
                boolean isBuyer = (t % 2 == 0);
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            Order o = new Order(idGen.getAndIncrement(),
                                isBuyer ? OrderSide.BUY : OrderSide.SELL,
                                500L, QTY_1, OrderType.LIMIT, aid, BTC_BRL);
                            eng.placeOrder(o);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            ready.await();
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS), "Threads did not finish in time");
            pool.shutdown();

            OrderBook book = eng.getOrderBook(BTC_BRL);
            // total buys == total sells, so book should be empty after all match
            assertTrue(book.getBids().isEmpty() && book.getAsks().isEmpty(),
                "Book should be empty when buy and sell volumes are equal");
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Order buy(long price, long qty) {
        return order(OrderSide.BUY, price, qty, BUYER_ID);
    }

    private Order sell(long price, long qty) {
        return order(OrderSide.SELL, price, qty, SELLER_ID);
    }

    private Order order(OrderSide side, long price, long qty, AccountId accountId) {
        return new Order(ids.getAndIncrement(), side, price, qty,
            OrderType.LIMIT, accountId, BTC_BRL);
    }

    private static Account funded(AccountId id, BigDecimal brl, BigDecimal btc) {
        Account a = new Account(id);
        if (brl.compareTo(BigDecimal.ZERO) > 0) {
            a.deposit(Asset.BRL, brl);
        }
        if (btc.compareTo(BigDecimal.ZERO) > 0) {
            a.deposit(Asset.BTC, btc);
        }
        return a;
    }

    private OrderBookEngine engine(List<Account> accounts) {
        return new OrderBookEngine(List.of(BTC_BRL), accountMap(accounts));
    }

    private static Map<AccountId, Account> accountMap(List<Account> accounts) {
        var map = new java.util.HashMap<AccountId, Account>();
        for (Account a : accounts) {
            map.put(a.getId(), a);
        }
        return map;
    }
}
