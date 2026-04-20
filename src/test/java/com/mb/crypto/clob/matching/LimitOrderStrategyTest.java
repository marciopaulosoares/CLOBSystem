package com.mb.crypto.clob.matching;

import com.mb.crypto.clob.domain.*;
import com.mb.crypto.clob.orderbook.OrderBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LimitOrderStrategyTest {

    private static final Instrument BTC_BRL  = new Instrument(Asset.BTC, Asset.BRL);
    private static final AccountId  BUYER_ID  = new AccountId("buyer");
    private static final AccountId  SELLER_ID = new AccountId("seller");

    private LimitOrderStrategy strategy;
    private OrderBook orderBook;
    private Map<AccountId, Account> accounts;

    @BeforeEach
    void setUp() {
        strategy  = new LimitOrderStrategy();
        orderBook = new OrderBook(BTC_BRL);
        accounts  = Collections.emptyMap();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Order buy(long id, String price, String qty) {
        return new Order(id, OrderSide.BUY, new BigDecimal(price), new BigDecimal(qty),
            OrderType.LIMIT, BUYER_ID, BTC_BRL);
    }

    private Order sell(long id, String price, String qty) {
        return new Order(id, OrderSide.SELL, new BigDecimal(price), new BigDecimal(qty),
            OrderType.LIMIT, SELLER_ID, BTC_BRL);
    }

    // ── test groups ──────────────────────────────────────────────────────────

    @Nested
    class NoMatch {

        @Test
        void shouldReturnEmptyListWhenBookIsEmpty() {
            List<MatchedPair> result = strategy.match(buy(1, "150000", "1"), orderBook, accounts);
            assertTrue(result.isEmpty());
        }

        @Test
        void shouldNotMatchBuyWhenBestAskExceedsLimit() {
            orderBook.addOrder(sell(10, "160000", "1"));
            List<MatchedPair> result = strategy.match(buy(1, "150000", "1"), orderBook, accounts);
            assertTrue(result.isEmpty());
        }

        @Test
        void shouldNotMatchSellWhenBestBidIsBelowLimit() {
            orderBook.addOrder(buy(10, "140000", "1"));
            List<MatchedPair> result = strategy.match(sell(1, "150000", "1"), orderBook, accounts);
            assertTrue(result.isEmpty());
        }

        @Test
        void shouldLeaveIncomingQuantityUnchangedWhenNoMatch() {
            Order incoming = buy(1, "150000", "2");
            strategy.match(incoming, orderBook, accounts);
            assertEquals(new BigDecimal("2"), incoming.getQuantity());
        }

        @Test
        void shouldLeaveBookUnchangedWhenNoMatch() {
            orderBook.addOrder(sell(10, "160000", "1"));
            strategy.match(buy(1, "150000", "1"), orderBook, accounts);
            assertEquals(1, orderBook.getAsks().get(new BigDecimal("160000")).size());
        }
    }

    @Nested
    class FullFill {

        @Test
        void shouldFullyFillBuyAgainstExactAsk() {
            Order ask = sell(10, "150000", "1");
            orderBook.addOrder(ask);
            Order incoming = buy(1, "150000", "1");

            List<MatchedPair> result = strategy.match(incoming, orderBook, accounts);

            assertEquals(1, result.size());
            assertEquals(new BigDecimal("0"), incoming.getQuantity());
            assertEquals(new BigDecimal("0"), ask.getQuantity());
        }

        @Test
        void shouldFullyFillSellAgainstExactBid() {
            Order bid = buy(10, "150000", "1");
            orderBook.addOrder(bid);
            Order incoming = sell(1, "150000", "1");

            List<MatchedPair> result = strategy.match(incoming, orderBook, accounts);

            assertEquals(1, result.size());
            assertEquals(new BigDecimal("0"), incoming.getQuantity());
            assertEquals(new BigDecimal("0"), bid.getQuantity());
        }

        @Test
        void shouldExecuteAtRestingAskPriceNotBuyLimit() {
            orderBook.addOrder(sell(10, "148000", "1"));
            Order incoming = buy(1, "150000", "1");

            MatchedPair pair = strategy.match(incoming, orderBook, accounts).get(0);

            assertEquals(new BigDecimal("148000"), pair.price());
        }

        @Test
        void shouldExecuteAtRestingBidPriceNotSellLimit() {
            orderBook.addOrder(buy(10, "152000", "1"));
            Order incoming = sell(1, "150000", "1");

            MatchedPair pair = strategy.match(incoming, orderBook, accounts).get(0);

            assertEquals(new BigDecimal("152000"), pair.price());
        }
    }

    @Nested
    class PartialFill {

        @Test
        void shouldConsumeIncomingBuyWhenSmallerThanResting() {
            Order ask = sell(10, "150000", "5");
            orderBook.addOrder(ask);
            Order incoming = buy(1, "150000", "2");

            List<MatchedPair> result = strategy.match(incoming, orderBook, accounts);

            assertEquals(1, result.size());
            assertEquals(new BigDecimal("2"), result.get(0).qty());
            assertEquals(new BigDecimal("0"), incoming.getQuantity());
            assertEquals(new BigDecimal("3"), ask.getQuantity());
        }

        @Test
        void shouldConsumeIncomingSellWhenSmallerThanResting() {
            Order bid = buy(10, "150000", "5");
            orderBook.addOrder(bid);
            Order incoming = sell(1, "150000", "2");

            List<MatchedPair> result = strategy.match(incoming, orderBook, accounts);

            assertEquals(1, result.size());
            assertEquals(new BigDecimal("2"), result.get(0).qty());
            assertEquals(new BigDecimal("0"), incoming.getQuantity());
            assertEquals(new BigDecimal("3"), bid.getQuantity());
        }

        @Test
        void shouldRetainPartiallyFilledRestingOrderInBook() {
            orderBook.addOrder(sell(10, "150000", "5"));
            strategy.match(buy(1, "150000", "2"), orderBook, accounts);
            assertFalse(orderBook.getAsks().isEmpty());
        }
    }

    @Nested
    class PricePriority {

        @Test
        void shouldMatchLowestAskFirstForBuyOrder() {
            orderBook.addOrder(sell(10, "152000", "1"));
            orderBook.addOrder(sell(11, "150000", "1"));
            Order incoming = buy(1, "155000", "1");

            MatchedPair pair = strategy.match(incoming, orderBook, accounts).get(0);

            assertEquals(new BigDecimal("150000"), pair.price());
        }

        @Test
        void shouldMatchHighestBidFirstForSellOrder() {
            orderBook.addOrder(buy(10, "148000", "1"));
            orderBook.addOrder(buy(11, "150000", "1"));
            Order incoming = sell(1, "145000", "1");

            MatchedPair pair = strategy.match(incoming, orderBook, accounts).get(0);

            assertEquals(new BigDecimal("150000"), pair.price());
        }

        @Test
        void shouldSweepMultiplePriceLevelsInPriorityOrder() {
            orderBook.addOrder(sell(10, "150000", "1"));
            orderBook.addOrder(sell(11, "151000", "1"));
            Order incoming = buy(1, "155000", "2");

            List<MatchedPair> result = strategy.match(incoming, orderBook, accounts);

            assertEquals(2, result.size());
            assertEquals(new BigDecimal("150000"), result.get(0).price());
            assertEquals(new BigDecimal("151000"), result.get(1).price());
        }
    }

    @Nested
    class TimePriority {

        @Test
        void shouldMatchFirstRestingOrderAtSamePriceLevelFirst() {
            Order firstAsk  = sell(10, "150000", "1");
            Order secondAsk = sell(11, "150000", "1");
            orderBook.addOrder(firstAsk);
            orderBook.addOrder(secondAsk);

            MatchedPair pair = strategy.match(buy(1, "150000", "1"), orderBook, accounts).get(0);

            assertSame(firstAsk, pair.resting());
        }

        @Test
        void shouldMatchSecondRestingOrderAfterFirstIsFullyConsumed() {
            Order firstAsk  = sell(10, "150000", "1");
            Order secondAsk = sell(11, "150000", "1");
            orderBook.addOrder(firstAsk);
            orderBook.addOrder(secondAsk);
            Order incoming = buy(1, "150000", "2");

            List<MatchedPair> result = strategy.match(incoming, orderBook, accounts);

            assertEquals(2, result.size());
            assertSame(firstAsk,  result.get(0).resting());
            assertSame(secondAsk, result.get(1).resting());
        }
    }

    @Nested
    class BookMaintenance {

        @Test
        void shouldRemoveFullyFilledRestingOrderFromQueue() {
            orderBook.addOrder(sell(10, "150000", "1"));
            strategy.match(buy(1, "150000", "1"), orderBook, accounts);
            assertTrue(orderBook.getAsks().isEmpty());
        }

        @Test
        void shouldRemoveEmptyPriceLevelFromBook() {
            orderBook.addOrder(sell(10, "150000", "1"));
            orderBook.addOrder(sell(11, "151000", "1"));
            strategy.match(buy(1, "155000", "1"), orderBook, accounts);
            assertFalse(orderBook.getAsks().containsKey(new BigDecimal("150000")));
            assertTrue(orderBook.getAsks().containsKey(new BigDecimal("151000")));
        }

        @Test
        void shouldPreserveUnfilledRestingOrdersInBook() {
            orderBook.addOrder(sell(10, "150000", "5"));
            strategy.match(buy(1, "150000", "2"), orderBook, accounts);
            assertEquals(1, orderBook.getAsks().get(new BigDecimal("150000")).size());
        }

        @Test
        void shouldNotRemovePriceLevelIfRestingOrderStillHasRemainingQty() {
            orderBook.addOrder(sell(10, "150000", "10"));
            strategy.match(buy(1, "150000", "3"), orderBook, accounts);
            assertNotNull(orderBook.getAsks().get(new BigDecimal("150000")));
        }
    }

    @Nested
    class MatchedPairContents {

        @Test
        void shouldReferenceCorrectIncomingAndRestingOrders() {
            Order ask = sell(10, "150000", "3");
            orderBook.addOrder(ask);
            Order incoming = buy(1, "150000", "2");

            MatchedPair pair = strategy.match(incoming, orderBook, accounts).get(0);

            assertSame(incoming, pair.incoming());
            assertSame(ask, pair.resting());
        }

        @Test
        void shouldCarryRestingPriceNotIncomingLimit() {
            orderBook.addOrder(sell(10, "148000", "1"));
            Order incoming = buy(1, "155000", "1");

            MatchedPair pair = strategy.match(incoming, orderBook, accounts).get(0);

            assertEquals(new BigDecimal("148000"), pair.price());
        }

        @Test
        void shouldCarryMinOfIncomingAndRestingQtyAsTradeQty() {
            orderBook.addOrder(sell(10, "150000", "3"));
            Order incoming = buy(1, "150000", "2");

            MatchedPair pair = strategy.match(incoming, orderBook, accounts).get(0);

            assertEquals(new BigDecimal("2"), pair.qty());
        }
    }
}
