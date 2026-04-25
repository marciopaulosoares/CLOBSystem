package com.mb.crypto.clob.matching;

import com.mb.crypto.clob.ClobSystem;
import com.mb.crypto.clob.domain.Account;
import com.mb.crypto.clob.domain.AccountId;
import com.mb.crypto.clob.domain.Asset;
import com.mb.crypto.clob.domain.Instrument;
import com.mb.crypto.clob.domain.Order;
import com.mb.crypto.clob.domain.OrderSide;
import com.mb.crypto.clob.domain.OrderStatus;
import com.mb.crypto.clob.domain.OrderType;
import com.mb.crypto.clob.domain.Scales;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the full order-lifecycle flow through {@link ClobSystem}.
 *
 * <p>Covers: balance locking, matching, settlement, partial fills, cancellations,
 * and error paths. Exercises all layers: ClobSystem → OrderBookEngine → LimitOrderStrategy
 * → OrderBook.
 */
class OrderBookEngineTest {

    private static final Instrument INSTRUMENT = new Instrument(Asset.BTC, Asset.BRL);
    private static final long       PRICE      = 50_000L;
    private static final long       ONE_BTC    = Scales.QUANTITY_SCALE;
    private static final long       HALF_BTC   = ONE_BTC / 2;

    private final AtomicLong idGen = new AtomicLong(1);

    private Account    alice;
    private Account    bob;
    private ClobSystem clob;

    @BeforeEach
    void setUp() {
        alice = new Account(new AccountId("alice"));
        bob   = new Account(new AccountId("bob"));
        clob  = new ClobSystem(List.of(INSTRUMENT), List.of(alice, bob));
    }

    // -----------------------------------------------------------------------
    // Full fill — both sides fully matched
    // -----------------------------------------------------------------------

    @Test
    void fullFill_buyMatchesSell_bothFilledAndBalancesSettled() {
        alice.deposit(Asset.BTC, BigDecimal.ONE);   // alice sells 1 BTC
        bob.deposit(Asset.BRL, new BigDecimal("50000")); // bob buys with BRL

        Order ask = sell(alice, PRICE, ONE_BTC);
        Order bid = buy(bob, PRICE, ONE_BTC);

        clob.placeOrder(ask);
        clob.placeOrder(bid);

        assertEquals(OrderStatus.FILLED, ask.getStatus());
        assertEquals(OrderStatus.FILLED, bid.getStatus());

        // alice traded BTC for BRL
        assertEquals(0, alice.getBalance(Asset.BTC).compareTo(BigDecimal.ZERO));
        assertEquals(0, alice.getBalance(Asset.BRL).compareTo(new BigDecimal("50000")));

        // bob traded BRL for BTC
        assertEquals(0, bob.getBalance(Asset.BRL).compareTo(BigDecimal.ZERO));
        assertEquals(0, bob.getBalance(Asset.BTC).compareTo(BigDecimal.ONE));

        assertTrue(clob.getOrderBook(INSTRUMENT).askPrices().isEmpty());
        assertTrue(clob.getOrderBook(INSTRUMENT).bidPrices().isEmpty());
    }

    // -----------------------------------------------------------------------
    // No match — resting in book
    // -----------------------------------------------------------------------

    @Test
    void noMatch_sellRestsInBook_statusOpen() {
        alice.deposit(Asset.BTC, BigDecimal.ONE);

        Order ask = sell(alice, PRICE, ONE_BTC);
        clob.placeOrder(ask);

        assertEquals(OrderStatus.OPEN, ask.getStatus());
        assertEquals(1, clob.getOrderBook(INSTRUMENT).askPrices().size());
        assertTrue(clob.getOrderBook(INSTRUMENT).askPrices().contains(PRICE));
    }

    @Test
    void noMatch_buyRestsInBook_statusOpen() {
        bob.deposit(Asset.BRL, new BigDecimal("50000"));

        Order bid = buy(bob, PRICE, ONE_BTC);
        clob.placeOrder(bid);

        assertEquals(OrderStatus.OPEN, bid.getStatus());
        assertEquals(1, clob.getOrderBook(INSTRUMENT).bidPrices().size());
    }

    // -----------------------------------------------------------------------
    // Partial fill — aggressor smaller than resting
    // -----------------------------------------------------------------------

    @Test
    void partialFill_aggressorSmallerThanResting_aggressorFilledRestingStays() {
        alice.deposit(Asset.BTC, BigDecimal.ONE);
        bob.deposit(Asset.BRL, new BigDecimal("25000"));

        Order ask = sell(alice, PRICE, ONE_BTC);
        Order bid = buy(bob, PRICE, HALF_BTC);    // bob buys only half

        clob.placeOrder(ask);
        clob.placeOrder(bid);

        assertEquals(OrderStatus.FILLED,           bid.getStatus());
        assertEquals(OrderStatus.PARTIALLY_FILLED, ask.getStatus());
        assertEquals(HALF_BTC, ask.getQuantityLong());

        // resting sell still has half a BTC in the book
        assertEquals(1, clob.getOrderBook(INSTRUMENT).levelSize(PRICE, OrderSide.SELL));
    }

    // -----------------------------------------------------------------------
    // Partial fill — resting smaller than aggressor
    // -----------------------------------------------------------------------

    @Test
    void partialFill_restingSmallerThanAggressor_restingFilledAggressorRests() {
        alice.deposit(Asset.BTC, new BigDecimal("0.5"));
        bob.deposit(Asset.BRL, new BigDecimal("50000"));

        Order ask = sell(alice, PRICE, HALF_BTC);  // alice sells half
        Order bid = buy(bob, PRICE, ONE_BTC);       // bob wants a full BTC

        clob.placeOrder(ask);
        clob.placeOrder(bid);

        assertEquals(OrderStatus.FILLED,           ask.getStatus());
        assertEquals(OrderStatus.PARTIALLY_FILLED, bid.getStatus());
        assertEquals(HALF_BTC, bid.getQuantityLong());

        // bob's remainder rests as a bid
        assertEquals(1, clob.getOrderBook(INSTRUMENT).bidPrices().size());
    }

    // -----------------------------------------------------------------------
    // Cancel resting order
    // -----------------------------------------------------------------------

    @Test
    void cancelOrder_restsOrder_statusCanceledAndBalanceReleased() {
        alice.deposit(Asset.BTC, BigDecimal.ONE);

        Order ask = sell(alice, PRICE, ONE_BTC);
        clob.placeOrder(ask);

        // BTC is now locked
        assertEquals(0, alice.getAvailableBalance(Asset.BTC).compareTo(BigDecimal.ZERO));
        assertEquals(0, alice.getLockedBalance(Asset.BTC).compareTo(BigDecimal.ONE));

        clob.cancelOrder(ask);

        assertEquals(OrderStatus.CANCELED, ask.getStatus());
        assertTrue(clob.getOrderBook(INSTRUMENT).askPrices().isEmpty());

        // BTC is unlocked and available again
        assertEquals(0, alice.getAvailableBalance(Asset.BTC).compareTo(BigDecimal.ONE));
        assertEquals(0, alice.getLockedBalance(Asset.BTC).compareTo(BigDecimal.ZERO));
    }

    @Test
    void cancelOrder_buyOrder_releasesLockedBrl() {
        bob.deposit(Asset.BRL, new BigDecimal("50000"));

        Order bid = buy(bob, PRICE, ONE_BTC);
        clob.placeOrder(bid);

        assertEquals(0, bob.getLockedBalance(Asset.BRL).compareTo(new BigDecimal("50000")));

        clob.cancelOrder(bid);

        assertEquals(OrderStatus.CANCELED, bid.getStatus());
        assertEquals(0, bob.getAvailableBalance(Asset.BRL).compareTo(new BigDecimal("50000")));
        assertEquals(0, bob.getLockedBalance(Asset.BRL).compareTo(BigDecimal.ZERO));
    }

    // -----------------------------------------------------------------------
    // Multi-level sweep
    // -----------------------------------------------------------------------

    @Test
    void sweep_buyMatchesMultipleSellLevels_allFilled() {
        Account carol = new Account(new AccountId("carol"));
        clob.addAccount(carol);

        alice.deposit(Asset.BTC, BigDecimal.ONE);
        carol.deposit(Asset.BTC, BigDecimal.ONE);
        bob.deposit(Asset.BRL, new BigDecimal("100000")); // enough for 2 BTC at 500

        Order askAlice = sell(alice, 490L, ONE_BTC);
        Order askCarol = sell(carol, 500L, ONE_BTC);
        Order bidBob   = buy(bob,   500L, ONE_BTC * 2);

        clob.placeOrder(askAlice);
        clob.placeOrder(askCarol);
        clob.placeOrder(bidBob);

        assertEquals(OrderStatus.FILLED, askAlice.getStatus());
        assertEquals(OrderStatus.FILLED, askCarol.getStatus());
        assertEquals(OrderStatus.FILLED, bidBob.getStatus());

        assertTrue(clob.getOrderBook(INSTRUMENT).askPrices().isEmpty());
        // bob received 2 BTC total
        assertEquals(0, bob.getBalance(Asset.BTC).compareTo(new BigDecimal("2.00000000")));
    }

    // -----------------------------------------------------------------------
    // Price-time priority (FIFO within a level)
    // -----------------------------------------------------------------------

    @Test
    void priceTimePriority_firstRestingOrderFilledFirst() {
        Account carol = new Account(new AccountId("carol"));
        clob.addAccount(carol);

        alice.deposit(Asset.BTC, new BigDecimal("0.5"));
        carol.deposit(Asset.BTC, new BigDecimal("0.5"));
        bob.deposit(Asset.BRL, new BigDecimal("25000"));

        Order askFirst  = sell(alice, PRICE, HALF_BTC); // rests first
        Order askSecond = sell(carol, PRICE, HALF_BTC);
        Order bid       = buy(bob,   PRICE, HALF_BTC);  // only enough for one

        clob.placeOrder(askFirst);
        clob.placeOrder(askSecond);
        clob.placeOrder(bid);

        // alice's order (first) was matched; carol's is still resting
        assertEquals(OrderStatus.FILLED, askFirst.getStatus());
        assertEquals(OrderStatus.OPEN,   askSecond.getStatus());
        assertEquals(1, clob.getOrderBook(INSTRUMENT).levelSize(PRICE, OrderSide.SELL));
    }

    // -----------------------------------------------------------------------
    // Error paths
    // -----------------------------------------------------------------------

    @Test
    void cancelOrder_filledOrder_throws() {
        alice.deposit(Asset.BTC, BigDecimal.ONE);
        bob.deposit(Asset.BRL, new BigDecimal("50000"));

        Order ask = sell(alice, PRICE, ONE_BTC);
        Order bid = buy(bob, PRICE, ONE_BTC);
        clob.placeOrder(ask);
        clob.placeOrder(bid);

        // ask is now FILLED — cancelling it should fail validation
        assertThrows(IllegalStateException.class, () -> clob.cancelOrder(ask));
    }

    @Test
    void placeOrder_insufficientBrlBalance_throws() {
        bob.deposit(Asset.BRL, new BigDecimal("1000")); // not enough for 50_000 BRL notional

        Order bid = buy(bob, PRICE, ONE_BTC);

        assertThrows(IllegalArgumentException.class, () -> clob.placeOrder(bid));
    }

    @Test
    void placeOrder_insufficientBtcBalance_throws() {
        // alice has no BTC

        Order ask = sell(alice, PRICE, ONE_BTC);

        assertThrows(IllegalArgumentException.class, () -> clob.placeOrder(ask));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Order sell(Account account, long price, long qty) {
        return new Order(idGen.getAndIncrement(), OrderSide.SELL, price, qty,
                         OrderType.LIMIT, account.getId(), INSTRUMENT);
    }

    private Order buy(Account account, long price, long qty) {
        return new Order(idGen.getAndIncrement(), OrderSide.BUY, price, qty,
                         OrderType.LIMIT, account.getId(), INSTRUMENT);
    }
}
