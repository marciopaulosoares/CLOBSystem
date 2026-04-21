package com.mb.crypto.clob.matching;

import com.mb.crypto.clob.domain.Account;
import com.mb.crypto.clob.domain.AccountId;
import com.mb.crypto.clob.domain.Asset;
import com.mb.crypto.clob.domain.Instrument;
import com.mb.crypto.clob.domain.Order;
import com.mb.crypto.clob.domain.OrderSide;
import com.mb.crypto.clob.domain.OrderStatus;
import com.mb.crypto.clob.domain.OrderType;
import com.mb.crypto.clob.orderbook.OrderBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimitOrderStrategyTest {

    private static final Instrument BTC_BRL = new Instrument(Asset.BTC, Asset.BRL);
    private static final AccountId ACCOUNT_A = new AccountId("A");
    private static final AccountId ACCOUNT_B = new AccountId("B");

    private LimitOrderStrategy strategy;
    private OrderBook book;
    private Map<AccountId, Account> accounts;

    @BeforeEach
    void setUp() {
        strategy = new LimitOrderStrategy();
        book = new OrderBook(BTC_BRL);
        accounts = new HashMap<>();
        accounts.put(ACCOUNT_A, new Account(ACCOUNT_A));
        accounts.put(ACCOUNT_B, new Account(ACCOUNT_B));
    }

    private Order order(long id, OrderSide side, String price, String qty) {
        return new Order(id, side, new BigDecimal(price), new BigDecimal(qty),
            OrderType.LIMIT, side == OrderSide.BUY ? ACCOUNT_A : ACCOUNT_B, BTC_BRL);
    }

    @Nested
    class NoMatch {

        @Test
        void buyPriceBelowBestAsk_returnsNoMatches() {
            Order ask = order(1, OrderSide.SELL, "500", "1");
            book.addOrder(ask);

            Order buy = order(2, OrderSide.BUY, "499", "1");
            List<MatchedPair> matches = strategy.match(buy, book, accounts);

            assertTrue(matches.isEmpty());
            assertEquals(new BigDecimal("1"), buy.getQuantity());
        }

        @Test
        void sellPriceAboveBestBid_returnsNoMatches() {
            Order bid = order(1, OrderSide.BUY, "500", "1");
            book.addOrder(bid);

            Order sell = order(2, OrderSide.SELL, "501", "1");
            List<MatchedPair> matches = strategy.match(sell, book, accounts);

            assertTrue(matches.isEmpty());
            assertEquals(new BigDecimal("1"), sell.getQuantity());
        }

        @Test
        void emptyBook_returnsNoMatches() {
            Order buy = order(1, OrderSide.BUY, "500", "1");
            List<MatchedPair> matches = strategy.match(buy, book, accounts);

            assertTrue(matches.isEmpty());
        }
    }

    @Nested
    class FullFill {

        @Test
        void buyMatchesAskAtSamePrice() {
            Order ask = order(1, OrderSide.SELL, "500", "1");
            book.addOrder(ask);

            Order buy = order(2, OrderSide.BUY, "500", "1");
            List<MatchedPair> matches = strategy.match(buy, book, accounts);

            assertEquals(1, matches.size());
            assertEquals(500L, matches.get(0).price());
            assertEquals(100_000_000L, matches.get(0).qty());
            assertEquals(BigDecimal.ZERO, buy.getQuantity());
            assertEquals(BigDecimal.ZERO, ask.getQuantity());
        }

        @Test
        void buyMatchesAskAtBetterPrice() {
            Order ask = order(1, OrderSide.SELL, "490", "1");
            book.addOrder(ask);

            Order buy = order(2, OrderSide.BUY, "500", "1");
            List<MatchedPair> matches = strategy.match(buy, book, accounts);

            assertEquals(1, matches.size());
            assertEquals(490L, matches.get(0).price(), "trade executes at resting (ask) price");
            assertEquals(BigDecimal.ZERO, buy.getQuantity());
        }

        @Test
        void sellMatchesBidAtSamePrice() {
            Order bid = order(1, OrderSide.BUY, "500", "2");
            book.addOrder(bid);

            Order sell = order(2, OrderSide.SELL, "500", "2");
            List<MatchedPair> matches = strategy.match(sell, book, accounts);

            assertEquals(1, matches.size());
            assertEquals(BigDecimal.ZERO, sell.getQuantity());
            assertEquals(BigDecimal.ZERO, bid.getQuantity());
        }
    }

    @Nested
    class PartialFill {

        @Test
        void incomingLargerThanResting_remainderLeftOnIncoming() {
            Order ask = order(1, OrderSide.SELL, "500", "0.5");
            book.addOrder(ask);

            Order buy = order(2, OrderSide.BUY, "500", "1");
            List<MatchedPair> matches = strategy.match(buy, book, accounts);

            assertEquals(1, matches.size());
            assertEquals(new BigDecimal("0.5"), buy.getQuantity());
            assertEquals(BigDecimal.ZERO, ask.getQuantity());
        }

        @Test
        void restingLargerThanIncoming_remainderLeftOnResting() {
            Order ask = order(1, OrderSide.SELL, "500", "2");
            book.addOrder(ask);

            Order buy = order(2, OrderSide.BUY, "500", "1");
            List<MatchedPair> matches = strategy.match(buy, book, accounts);

            assertEquals(1, matches.size());
            assertEquals(BigDecimal.ZERO, buy.getQuantity());
            assertEquals(new BigDecimal("1"), ask.getQuantity());
        }
    }

    @Nested
    class MultiLevelMatching {

        @Test
        void buyConsumesMultiplePriceLevels() {
            Order ask1 = order(1, OrderSide.SELL, "490", "0.5");
            Order ask2 = order(2, OrderSide.SELL, "495", "0.5");
            book.addOrder(ask1);
            book.addOrder(ask2);

            Order buy = order(3, OrderSide.BUY, "500", "1");
            List<MatchedPair> matches = strategy.match(buy, book, accounts);

            assertEquals(2, matches.size());
            assertEquals(490L, matches.get(0).price());
            assertEquals(495L, matches.get(1).price());
            assertEquals(BigDecimal.ZERO, buy.getQuantity());
        }

        @Test
        void buyConsumesSameLevelFifo() {
            Order ask1 = order(1, OrderSide.SELL, "500", "0.3");
            Order ask2 = order(2, OrderSide.SELL, "500", "0.7");
            book.addOrder(ask1);
            book.addOrder(ask2);

            Order buy = order(3, OrderSide.BUY, "500", "1");
            List<MatchedPair> matches = strategy.match(buy, book, accounts);

            assertEquals(2, matches.size());
            assertEquals(ask1, matches.get(0).resting(), "first-in order matched first");
            assertEquals(ask2, matches.get(1).resting());
            assertEquals(BigDecimal.ZERO, buy.getQuantity());
        }

        @Test
        void sellConsumesMultipleBidLevels() {
            Order bid1 = order(1, OrderSide.BUY, "510", "0.4");
            Order bid2 = order(2, OrderSide.BUY, "505", "0.6");
            book.addOrder(bid1);
            book.addOrder(bid2);

            Order sell = order(3, OrderSide.SELL, "500", "1");
            List<MatchedPair> matches = strategy.match(sell, book, accounts);

            assertEquals(2, matches.size());
            assertEquals(510L, matches.get(0).price(), "highest bid matched first");
            assertEquals(505L, matches.get(1).price());
            assertEquals(BigDecimal.ZERO, sell.getQuantity());
        }
    }

    @Nested
    class OrderStatusAfterMatch {

        @Test
        void fullyFilledIncomingStatusNotChangedByStrategy() {
            Order ask = order(1, OrderSide.SELL, "500", "1");
            book.addOrder(ask);

            Order buy = order(2, OrderSide.BUY, "500", "1");
            strategy.match(buy, book, accounts);

            // strategy only adjusts quantity — status is managed by OrderBookEngine
            assertEquals(OrderStatus.OPEN, buy.getStatus());
        }

        @Test
        void emptyQueuePurgedFromBook() {
            Order ask = order(1, OrderSide.SELL, "500", "1");
            book.addOrder(ask);

            Order buy = order(2, OrderSide.BUY, "500", "1");
            strategy.match(buy, book, accounts);

            assertTrue(book.getAsks().isEmpty(), "empty price level must be removed from book");
        }

        @Test
        void partiallyFilledRestingRemainsInBook() {
            Order ask = order(1, OrderSide.SELL, "500", "2");
            book.addOrder(ask);

            Order buy = order(2, OrderSide.BUY, "500", "1");
            strategy.match(buy, book, accounts);

            assertEquals(1, book.getAsks().get(500L).size(), "resting order still in book");
        }
    }
}
