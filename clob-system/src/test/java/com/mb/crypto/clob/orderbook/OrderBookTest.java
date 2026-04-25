package com.mb.crypto.clob.orderbook;

import com.mb.crypto.clob.domain.AccountId;
import com.mb.crypto.clob.domain.Asset;
import com.mb.crypto.clob.domain.Instrument;
import com.mb.crypto.clob.domain.Order;
import com.mb.crypto.clob.domain.OrderSide;
import com.mb.crypto.clob.domain.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the ByteBuffer-backed doubly-linked-list {@link OrderBook}.
 *
 * <p>Focuses on the O(1) cancel (pointer surgery) and FIFO ordering guarantees.
 * No engine or accounts involved — OrderBook is exercised directly.
 */
class OrderBookTest {

    private static final Instrument INSTRUMENT = new Instrument(Asset.BTC, Asset.BRL);
    private static final AccountId  ACCOUNT    = new AccountId("test");
    private static final long       PRICE      = 50_000L;
    private static final long       QTY        = 100_000_000L; // 1 BTC

    private OrderBook book;
    private long      nextId;

    @BeforeEach
    void setUp() {
        book   = new OrderBook(INSTRUMENT);
        nextId = 1L;
    }

    // -----------------------------------------------------------------------
    // addOrder — level creation and basic lookup
    // -----------------------------------------------------------------------

    @Test
    void addBidOrder_createsLevelAndPeeksCorrectly() {
        Order bid = bid(PRICE, QTY);
        book.addOrder(bid);

        assertTrue(book.bidPrices().contains(PRICE));
        assertSame(bid, book.peekHead(PRICE, OrderSide.BUY));
        assertEquals(1, book.levelSize(PRICE, OrderSide.BUY));
    }

    @Test
    void addAskOrder_createsLevelAndPeeksCorrectly() {
        Order ask = ask(PRICE, QTY);
        book.addOrder(ask);

        assertTrue(book.askPrices().contains(PRICE));
        assertSame(ask, book.peekHead(PRICE, OrderSide.SELL));
        assertEquals(1, book.levelSize(PRICE, OrderSide.SELL));
    }

    @Test
    void addMultipleOrdersSameLevel_levelSizeGrowsCorrectly() {
        book.addOrder(bid(PRICE, QTY));
        book.addOrder(bid(PRICE, QTY));
        book.addOrder(bid(PRICE, QTY));

        assertEquals(3, book.levelSize(PRICE, OrderSide.BUY));
    }

    @Test
    void addOrdersDifferentPriceLevels_separateLevelsCreated() {
        book.addOrder(ask(500L, QTY));
        book.addOrder(ask(510L, QTY));

        assertEquals(2, book.askPrices().size());
        assertTrue(book.askPrices().contains(500L));
        assertTrue(book.askPrices().contains(510L));
    }

    // -----------------------------------------------------------------------
    // pollHead — FIFO ordering and level cleanup
    // -----------------------------------------------------------------------

    @Test
    void pollHead_returnsFifoOrder() {
        Order first  = bid(PRICE, QTY);
        Order second = bid(PRICE, QTY);
        Order third  = bid(PRICE, QTY);
        book.addOrder(first);
        book.addOrder(second);
        book.addOrder(third);

        assertSame(first,  book.pollHead(PRICE, OrderSide.BUY));
        assertSame(second, book.pollHead(PRICE, OrderSide.BUY));
        assertSame(third,  book.pollHead(PRICE, OrderSide.BUY));
    }

    @Test
    void pollHead_removesLevelWhenEmpty() {
        Order o = bid(PRICE, QTY);
        book.addOrder(o);

        book.pollHead(PRICE, OrderSide.BUY);

        assertTrue(book.isLevelEmpty(PRICE, OrderSide.BUY));
        assertFalse(book.bidPrices().contains(PRICE));
    }

    @Test
    void pollHead_onEmptyLevel_returnsNull() {
        assertNull(book.pollHead(PRICE, OrderSide.BUY));
    }

    @Test
    void pollHead_decreasesLevelSize() {
        book.addOrder(bid(PRICE, QTY));
        book.addOrder(bid(PRICE, QTY));

        book.pollHead(PRICE, OrderSide.BUY);

        assertEquals(1, book.levelSize(PRICE, OrderSide.BUY));
    }

    // -----------------------------------------------------------------------
    // cancelOrder — O(1) pointer surgery at head, middle, and tail
    // -----------------------------------------------------------------------

    @Test
    void cancelOrder_singleOrder_removesLevel() {
        Order o = bid(PRICE, QTY);
        book.addOrder(o);

        book.cancelOrder(o);

        assertTrue(book.isLevelEmpty(PRICE, OrderSide.BUY));
        assertFalse(book.bidPrices().contains(PRICE));
    }

    @Test
    void cancelOrder_head_secondOrderBecomesHead() {
        Order first  = bid(PRICE, QTY);
        Order second = bid(PRICE, QTY);
        book.addOrder(first);
        book.addOrder(second);

        book.cancelOrder(first);

        assertEquals(1, book.levelSize(PRICE, OrderSide.BUY));
        assertSame(second, book.peekHead(PRICE, OrderSide.BUY));
    }

    @Test
    void cancelOrder_middle_remainingOrdersPreserveOrder() {
        Order first  = bid(PRICE, QTY);
        Order second = bid(PRICE, QTY);
        Order third  = bid(PRICE, QTY);
        book.addOrder(first);
        book.addOrder(second);
        book.addOrder(third);

        book.cancelOrder(second);

        assertEquals(2, book.levelSize(PRICE, OrderSide.BUY));
        assertSame(first, book.pollHead(PRICE, OrderSide.BUY));
        assertSame(third, book.pollHead(PRICE, OrderSide.BUY));
    }

    @Test
    void cancelOrder_tail_levelShrinksByOne() {
        Order first = bid(PRICE, QTY);
        Order tail  = bid(PRICE, QTY);
        book.addOrder(first);
        book.addOrder(tail);

        book.cancelOrder(tail);

        assertEquals(1, book.levelSize(PRICE, OrderSide.BUY));
        assertSame(first, book.peekHead(PRICE, OrderSide.BUY));
    }

    @Test
    void cancelOrder_unknownOrder_throwsIllegalArgument() {
        Order unknown = bid(PRICE, QTY);

        assertThrows(IllegalArgumentException.class, () -> book.cancelOrder(unknown));
    }

    @Test
    void cancelOrder_alreadyPolled_throwsIllegalArgument() {
        Order o = bid(PRICE, QTY);
        book.addOrder(o);
        book.pollHead(PRICE, OrderSide.BUY);

        assertThrows(IllegalArgumentException.class, () -> book.cancelOrder(o));
    }

    // -----------------------------------------------------------------------
    // bidPrices / askPrices — ordering invariants
    // -----------------------------------------------------------------------

    @Test
    void bidPrices_orderedDescending_highestFirst() {
        book.addOrder(bid(490L, QTY));
        book.addOrder(bid(510L, QTY));
        book.addOrder(bid(500L, QTY));

        Long[] prices = book.bidPrices().toArray(new Long[0]);

        assertEquals(510L, prices[0]);
        assertEquals(500L, prices[1]);
        assertEquals(490L, prices[2]);
    }

    @Test
    void askPrices_orderedAscending_lowestFirst() {
        book.addOrder(ask(510L, QTY));
        book.addOrder(ask(490L, QTY));
        book.addOrder(ask(500L, QTY));

        Long[] prices = book.askPrices().toArray(new Long[0]);

        assertEquals(490L, prices[0]);
        assertEquals(500L, prices[1]);
        assertEquals(510L, prices[2]);
    }

    @Test
    void peekHead_onNonExistentLevel_returnsNull() {
        assertNull(book.peekHead(PRICE, OrderSide.BUY));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Order bid(long price, long qty) {
        return new Order(nextId++, OrderSide.BUY, price, qty, OrderType.LIMIT, ACCOUNT, INSTRUMENT);
    }

    private Order ask(long price, long qty) {
        return new Order(nextId++, OrderSide.SELL, price, qty, OrderType.LIMIT, ACCOUNT, INSTRUMENT);
    }
}
