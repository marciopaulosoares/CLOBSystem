package com.mb.crypto.clob.matching;

import com.mb.crypto.clob.domain.AccountId;
import com.mb.crypto.clob.domain.Asset;
import com.mb.crypto.clob.domain.Instrument;
import com.mb.crypto.clob.domain.Order;
import com.mb.crypto.clob.domain.OrderSide;
import com.mb.crypto.clob.domain.OrderType;
import com.mb.crypto.clob.orderbook.OrderBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LimitOrderStrategyTest {

    private static final Instrument INSTRUMENT   = new Instrument(Asset.BTC, Asset.BRL);
    private static final AccountId  BUYER_ID     = new AccountId("buyer");
    private static final AccountId  SELLER_ID    = new AccountId("seller");

    private static final long QTY_1 = 100_000_000L;  // 1 BTC in satoshis
    private static final long QTY_2 = 200_000_000L;  // 2 BTC in satoshis

    private LimitOrderStrategy strategy;
    private OrderBook orderBook;

    private Order buyOrder(long id, long price, long qty) {
        return new Order(id, OrderSide.BUY, price, qty, OrderType.LIMIT, BUYER_ID, INSTRUMENT);
    }

    private Order sellOrder(long id, long price, long qty) {
        return new Order(id, OrderSide.SELL, price, qty, OrderType.LIMIT, SELLER_ID, INSTRUMENT);
    }

    @BeforeEach
    void setUp() {
        strategy  = new LimitOrderStrategy();
        orderBook = new OrderBook(INSTRUMENT);
    }

    // -------------------------------------------------------------------------
    // No match scenarios
    // -------------------------------------------------------------------------

    @Nested
    class NoMatch {

        @Test
        void buyBelowBestAsk_returnsEmptyList() {
            orderBook.addOrder(sellOrder(1, 500L, QTY_1));   // ask at 500
            Order buy = buyOrder(2, 499L, QTY_1);            // bid at 499

            List<MatchedPair> result = strategy.match(buy, orderBook, Map.of());

            assertTrue(result.isEmpty());
        }

        @Test
        void sellAboveBestBid_returnsEmptyList() {
            orderBook.addOrder(buyOrder(1, 500L, QTY_1));    // bid at 500
            Order sell = sellOrder(2, 501L, QTY_1);          // ask at 501

            List<MatchedPair> result = strategy.match(sell, orderBook, Map.of());

            assertTrue(result.isEmpty());
        }

        @Test
        void emptyBook_returnsEmptyList() {
            Order buy = buyOrder(1, 500L, QTY_1);

            List<MatchedPair> result = strategy.match(buy, orderBook, Map.of());

            assertTrue(result.isEmpty());
        }

        @Test
        void incomingQuantityNotReducedOnMiss() {
            orderBook.addOrder(sellOrder(1, 600L, QTY_1));
            Order buy = buyOrder(2, 500L, QTY_1);

            strategy.match(buy, orderBook, Map.of());

            assertEquals(QTY_1, buy.getQuantityLong());
        }
    }

    // -------------------------------------------------------------------------
    // Full fill scenarios
    // -------------------------------------------------------------------------

    @Nested
    class FullFill {

        @Test
        void buyAtExactAskPrice_producesOneMatchedPair() {
            orderBook.addOrder(sellOrder(1, 500L, QTY_1));
            Order buy = buyOrder(2, 500L, QTY_1);

            List<MatchedPair> result = strategy.match(buy, orderBook, Map.of());

            assertEquals(1, result.size());
        }

        @Test
        void buyAtExactAskPrice_matchedPairHasCorrectPrice() {
            orderBook.addOrder(sellOrder(1, 500L, QTY_1));
            Order buy = buyOrder(2, 500L, QTY_1);

            MatchedPair pair = strategy.match(buy, orderBook, Map.of()).getFirst();

            assertEquals(500L, pair.price());
        }

        @Test
        void buyAtExactAskPrice_matchedPairHasCorrectQty() {
            orderBook.addOrder(sellOrder(1, 500L, QTY_1));
            Order buy = buyOrder(2, 500L, QTY_1);

            MatchedPair pair = strategy.match(buy, orderBook, Map.of()).getFirst();

            assertEquals(QTY_1, pair.qty());
        }

        @Test
        void buyAboveAskPrice_matchesAtAskPrice() {
            orderBook.addOrder(sellOrder(1, 490L, QTY_1));   // ask at 490
            Order buy = buyOrder(2, 500L, QTY_1);            // bid at 500

            MatchedPair pair = strategy.match(buy, orderBook, Map.of()).getFirst();

            assertEquals(490L, pair.price());
        }

        @Test
        void sellAtExactBidPrice_producesOneMatchedPair() {
            orderBook.addOrder(buyOrder(1, 500L, QTY_1));
            Order sell = sellOrder(2, 500L, QTY_1);

            List<MatchedPair> result = strategy.match(sell, orderBook, Map.of());

            assertEquals(1, result.size());
        }

        @Test
        void sellBelowBidPrice_matchesAtBidPrice() {
            orderBook.addOrder(buyOrder(1, 510L, QTY_1));    // bid at 510
            Order sell = sellOrder(2, 500L, QTY_1);          // ask at 500

            MatchedPair pair = strategy.match(sell, orderBook, Map.of()).getFirst();

            assertEquals(510L, pair.price());
        }

        @Test
        void incomingQuantityIsZeroAfterFullFill() {
            orderBook.addOrder(sellOrder(1, 500L, QTY_1));
            Order buy = buyOrder(2, 500L, QTY_1);

            strategy.match(buy, orderBook, Map.of());

            assertEquals(0L, buy.getQuantityLong());
        }

        @Test
        void restingQuantityIsZeroAfterFullFill() {
            Order resting = sellOrder(1, 500L, QTY_1);
            orderBook.addOrder(resting);
            Order buy = buyOrder(2, 500L, QTY_1);

            strategy.match(buy, orderBook, Map.of());

            assertEquals(0L, resting.getQuantityLong());
        }
    }

    // -------------------------------------------------------------------------
    // Partial fill scenarios
    // -------------------------------------------------------------------------

    @Nested
    class PartialFill {

        @Test
        void buyLargerThanRestingAsk_consumesRestingFully() {
            Order resting = sellOrder(1, 500L, QTY_1);
            orderBook.addOrder(resting);
            Order buy = buyOrder(2, 500L, QTY_2);            // 2 BTC incoming, 1 BTC resting

            strategy.match(buy, orderBook, Map.of());

            assertEquals(0L, resting.getQuantityLong());
        }

        @Test
        void buyLargerThanRestingAsk_incomingHasRemainder() {
            orderBook.addOrder(sellOrder(1, 500L, QTY_1));
            Order buy = buyOrder(2, 500L, QTY_2);

            strategy.match(buy, orderBook, Map.of());

            assertEquals(QTY_1, buy.getQuantityLong());      // 1 BTC remains
        }

        @Test
        void buyLargerThanRestingAsk_matchedQtyIsRestingQty() {
            orderBook.addOrder(sellOrder(1, 500L, QTY_1));
            Order buy = buyOrder(2, 500L, QTY_2);

            MatchedPair pair = strategy.match(buy, orderBook, Map.of()).getFirst();

            assertEquals(QTY_1, pair.qty());
        }

        @Test
        void buySmallerThanRestingAsk_restingHasRemainder() {
            Order resting = sellOrder(1, 500L, QTY_2);       // 2 BTC resting
            orderBook.addOrder(resting);
            Order buy = buyOrder(2, 500L, QTY_1);            // 1 BTC incoming

            strategy.match(buy, orderBook, Map.of());

            assertEquals(QTY_1, resting.getQuantityLong());  // 1 BTC remains
        }
    }

    // -------------------------------------------------------------------------
    // Multiple resting order scenarios
    // -------------------------------------------------------------------------

    @Nested
    class MultipleRestingOrders {

        @Test
        void buySweeepsTwoPriceLevels_returnsTwoMatchedPairs() {
            orderBook.addOrder(sellOrder(1, 490L, QTY_1));
            orderBook.addOrder(sellOrder(2, 495L, QTY_1));
            Order buy = buyOrder(3, 500L, QTY_2);

            List<MatchedPair> result = strategy.match(buy, orderBook, Map.of());

            assertEquals(2, result.size());
        }

        @Test
        void buySweeepsTwoPriceLevels_matchesInPriceTimeOrder() {
            orderBook.addOrder(sellOrder(1, 490L, QTY_1));
            orderBook.addOrder(sellOrder(2, 495L, QTY_1));
            Order buy = buyOrder(3, 500L, QTY_2);

            List<MatchedPair> result = strategy.match(buy, orderBook, Map.of());

            assertEquals(490L, result.get(0).price());
            assertEquals(495L, result.get(1).price());
        }

        @Test
        void buySweeepsTwoPriceLevels_incomingQuantityIsZero() {
            orderBook.addOrder(sellOrder(1, 490L, QTY_1));
            orderBook.addOrder(sellOrder(2, 495L, QTY_1));
            Order buy = buyOrder(3, 500L, QTY_2);

            strategy.match(buy, orderBook, Map.of());

            assertEquals(0L, buy.getQuantityLong());
        }

        @Test
        void twoOrdersSamePriceLevel_matchedInFifoOrder() {
            Order firstResting  = sellOrder(1, 500L, QTY_1);
            Order secondResting = sellOrder(2, 500L, QTY_1);
            orderBook.addOrder(firstResting);
            orderBook.addOrder(secondResting);
            Order buy = buyOrder(3, 500L, QTY_2);

            List<MatchedPair> result = strategy.match(buy, orderBook, Map.of());

            assertEquals(2, result.size());
            assertSame(firstResting,  result.get(0).resting());
            assertSame(secondResting, result.get(1).resting());
        }

        @Test
        void buyStopsAtPriceBoundary_doesNotConsumeRestingAboveLimit() {
            orderBook.addOrder(sellOrder(1, 490L, QTY_1));   // matches
            orderBook.addOrder(sellOrder(2, 510L, QTY_1));   // does not match
            Order buy = buyOrder(3, 500L, QTY_2);

            List<MatchedPair> result = strategy.match(buy, orderBook, Map.of());

            assertEquals(1, result.size());
            assertEquals(490L, result.getFirst().price());
        }

        @Test
        void buyStopsAtPriceBoundary_incomingHasRemainder() {
            orderBook.addOrder(sellOrder(1, 490L, QTY_1));
            orderBook.addOrder(sellOrder(2, 510L, QTY_1));
            Order buy = buyOrder(3, 500L, QTY_2);

            strategy.match(buy, orderBook, Map.of());

            assertEquals(QTY_1, buy.getQuantityLong());
        }
    }

    // -------------------------------------------------------------------------
    // Book cleanup after fill
    // -------------------------------------------------------------------------

    @Nested
    class BookCleanup {

        @Test
        void fullFill_emptyPriceLevelRemovedFromAsks() {
            orderBook.addOrder(sellOrder(1, 500L, QTY_1));
            Order buy = buyOrder(2, 500L, QTY_1);

            strategy.match(buy, orderBook, Map.of());

            assertFalse(orderBook.getAsks().containsKey(500L));
        }

        @Test
        void fullFill_emptyPriceLevelRemovedFromBids() {
            orderBook.addOrder(buyOrder(1, 500L, QTY_1));
            Order sell = sellOrder(2, 500L, QTY_1);

            strategy.match(sell, orderBook, Map.of());

            assertFalse(orderBook.getBids().containsKey(500L));
        }

        @Test
        void partialFill_priceLevelRemainsInBook() {
            orderBook.addOrder(sellOrder(1, 500L, QTY_2));   // 2 BTC resting
            Order buy = buyOrder(2, 500L, QTY_1);            // 1 BTC incoming

            strategy.match(buy, orderBook, Map.of());

            assertTrue(orderBook.getAsks().containsKey(500L));
        }
    }
}
