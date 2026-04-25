package com.mb.crypto.clob.matching;

import com.mb.crypto.clob.domain.AccountId;
import com.mb.crypto.clob.domain.Asset;
import com.mb.crypto.clob.domain.Instrument;
import com.mb.crypto.clob.domain.Order;
import com.mb.crypto.clob.domain.OrderSide;
import com.mb.crypto.clob.domain.OrderType;
import com.mb.crypto.clob.domain.Scales;
import com.mb.crypto.clob.orderbook.OrderBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link LimitOrderStrategy} matching logic.
 *
 * <p>Exercises the strategy in isolation: resting orders are added directly to a real
 * {@link OrderBook}; the engine and account settlement layers are not involved.
 * Assertions focus on matched quantities and the residual state of the book.
 */
class LimitOrderStrategyTest {

    private static final Instrument  INSTRUMENT   = new Instrument(Asset.BTC, Asset.BRL);
    private static final AccountId   BUYER_ID     = new AccountId("buyer");
    private static final AccountId   SELLER_ID    = new AccountId("seller");
    private static final long        PRICE        = 50_000L;
    private static final long        ONE_BTC      = Scales.QUANTITY_SCALE;
    private static final long        HALF_BTC     = ONE_BTC / 2;

    private OrderBook            book;
    private LimitOrderStrategy   strategy;
    private long                 nextId;

    @BeforeEach
    void setUp() {
        book     = new OrderBook(INSTRUMENT);
        strategy = new LimitOrderStrategy();
        nextId   = 1L;
    }

    // -----------------------------------------------------------------------
    // Full fill
    // -----------------------------------------------------------------------

    @Test
    void buy_fullFill_matchesExactSell() {
        Order resting  = addAsk(PRICE, ONE_BTC);
        Order incoming = buy(PRICE, ONE_BTC);

        List<MatchedPair> matches = strategy.match(incoming, book, Map.of());

        assertEquals(1, matches.size());
        assertSame(incoming, matches.get(0).incoming());
        assertSame(resting,  matches.get(0).resting());
        assertEquals(PRICE,   matches.get(0).price());
        assertEquals(ONE_BTC, matches.get(0).qty());
        assertEquals(0L,      incoming.getQuantityLong());
        assertTrue(book.isLevelEmpty(PRICE, OrderSide.SELL));
    }

    @Test
    void sell_fullFill_matchesExactBid() {
        Order resting  = addBid(PRICE, ONE_BTC);
        Order incoming = sell(PRICE, ONE_BTC);

        List<MatchedPair> matches = strategy.match(incoming, book, Map.of());

        assertEquals(1, matches.size());
        assertEquals(ONE_BTC, matches.get(0).qty());
        assertEquals(0L,      incoming.getQuantityLong());
        assertTrue(book.isLevelEmpty(PRICE, OrderSide.BUY));
    }

    // -----------------------------------------------------------------------
    // No match
    // -----------------------------------------------------------------------

    @Test
    void buy_limitBelowBestAsk_noMatch() {
        addAsk(PRICE, ONE_BTC);
        Order incoming = buy(PRICE - 1, ONE_BTC);

        List<MatchedPair> matches = strategy.match(incoming, book, Map.of());

        assertTrue(matches.isEmpty());
        assertEquals(ONE_BTC, incoming.getQuantityLong());
        assertEquals(1, book.levelSize(PRICE, OrderSide.SELL));
    }

    @Test
    void sell_limitAboveBestBid_noMatch() {
        addBid(PRICE, ONE_BTC);
        Order incoming = sell(PRICE + 1, ONE_BTC);

        List<MatchedPair> matches = strategy.match(incoming, book, Map.of());

        assertTrue(matches.isEmpty());
        assertEquals(ONE_BTC, incoming.getQuantityLong());
        assertEquals(1, book.levelSize(PRICE, OrderSide.BUY));
    }

    @Test
    void emptyBook_noMatch() {
        Order incoming = buy(PRICE, ONE_BTC);

        List<MatchedPair> matches = strategy.match(incoming, book, Map.of());

        assertTrue(matches.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Partial fill — aggressor exhausted first
    // -----------------------------------------------------------------------

    @Test
    void buy_smallerThanResting_aggressorExhausted() {
        addAsk(PRICE, ONE_BTC);
        Order incoming = buy(PRICE, HALF_BTC);

        List<MatchedPair> matches = strategy.match(incoming, book, Map.of());

        assertEquals(1, matches.size());
        assertEquals(HALF_BTC, matches.get(0).qty());
        assertEquals(0L,       incoming.getQuantityLong());
        assertEquals(HALF_BTC, book.peekHead(PRICE, OrderSide.SELL).getQuantityLong());
        assertEquals(1, book.levelSize(PRICE, OrderSide.SELL));
    }

    // -----------------------------------------------------------------------
    // Partial fill — resting order exhausted first, incoming rests remainder
    // -----------------------------------------------------------------------

    @Test
    void buy_largerThanResting_restingExhausted() {
        addAsk(PRICE, HALF_BTC);
        Order incoming = buy(PRICE, ONE_BTC);

        List<MatchedPair> matches = strategy.match(incoming, book, Map.of());

        assertEquals(1, matches.size());
        assertEquals(HALF_BTC, matches.get(0).qty());
        assertEquals(HALF_BTC, incoming.getQuantityLong());
        assertTrue(book.isLevelEmpty(PRICE, OrderSide.SELL));
    }

    // -----------------------------------------------------------------------
    // Multi-level sweep
    // -----------------------------------------------------------------------

    @Test
    void buy_sweepsMultiplePriceLevels_allFilled() {
        Order restingLow  = addAsk(490L, ONE_BTC);
        Order restingHigh = addAsk(500L, ONE_BTC);
        Order incoming    = buy(500L, ONE_BTC * 2);

        List<MatchedPair> matches = strategy.match(incoming, book, Map.of());

        assertEquals(2, matches.size());
        assertSame(restingLow,  matches.get(0).resting());
        assertEquals(490L,      matches.get(0).price());
        assertSame(restingHigh, matches.get(1).resting());
        assertEquals(500L,      matches.get(1).price());
        assertEquals(0L,        incoming.getQuantityLong());
        assertTrue(book.askPrices().isEmpty());
    }

    @Test
    void buy_sweepStopsAtPriceBoundary() {
        addAsk(490L, ONE_BTC);
        addAsk(510L, ONE_BTC);  // beyond the buy limit
        Order incoming = buy(500L, ONE_BTC * 2);

        List<MatchedPair> matches = strategy.match(incoming, book, Map.of());

        assertEquals(1, matches.size());
        assertEquals(490L,   matches.get(0).price());
        assertEquals(ONE_BTC, incoming.getQuantityLong());
        assertEquals(1, book.askPrices().size());
        assertTrue(book.askPrices().contains(510L));
    }

    // -----------------------------------------------------------------------
    // Price-time priority (FIFO within a price level)
    // -----------------------------------------------------------------------

    @Test
    void buy_priceTimePriority_fillsOldestRestingOrderFirst() {
        Order first  = addAsk(PRICE, HALF_BTC);
        Order second = addAsk(PRICE, HALF_BTC);
        Order incoming = buy(PRICE, HALF_BTC);

        List<MatchedPair> matches = strategy.match(incoming, book, Map.of());

        assertEquals(1, matches.size());
        assertSame(first, matches.get(0).resting());
        assertSame(second, book.peekHead(PRICE, OrderSide.SELL));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Order addAsk(long price, long qty) {
        Order o = new Order(nextId++, OrderSide.SELL, price, qty,
                            OrderType.LIMIT, SELLER_ID, INSTRUMENT);
        book.addOrder(o);
        return o;
    }

    private Order addBid(long price, long qty) {
        Order o = new Order(nextId++, OrderSide.BUY, price, qty,
                            OrderType.LIMIT, BUYER_ID, INSTRUMENT);
        book.addOrder(o);
        return o;
    }

    private Order buy(long price, long qty) {
        return new Order(nextId++, OrderSide.BUY, price, qty,
                         OrderType.LIMIT, BUYER_ID, INSTRUMENT);
    }

    private Order sell(long price, long qty) {
        return new Order(nextId++, OrderSide.SELL, price, qty,
                         OrderType.LIMIT, SELLER_ID, INSTRUMENT);
    }
}
