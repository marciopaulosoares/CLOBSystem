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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderBookEngineTest {

    private static final Instrument BTC_BRL = new Instrument(Asset.BTC, Asset.BRL);
    private static final AccountId BUYER_ID  = new AccountId("buyer");
    private static final AccountId SELLER_ID = new AccountId("seller");

    private Account buyer;
    private Account seller;
    private OrderBookEngine engine;

    @BeforeEach
    void setUp() {
        buyer  = new Account(BUYER_ID);
        seller = new Account(SELLER_ID);
        buyer.deposit(Asset.BRL, new BigDecimal("100000"));
        seller.deposit(Asset.BTC, new BigDecimal("10"));
        engine = new OrderBookEngine(List.of(BTC_BRL), Map.of(BUYER_ID, buyer, SELLER_ID, seller));
    }

    private Order buyOrder(long id, String price, String qty) {
        return new Order(id, OrderSide.BUY, new BigDecimal(price), new BigDecimal(qty),
            OrderType.LIMIT, BUYER_ID, BTC_BRL);
    }

    private Order sellOrder(long id, String price, String qty) {
        return new Order(id, OrderSide.SELL, new BigDecimal(price), new BigDecimal(qty),
            OrderType.LIMIT, SELLER_ID, BTC_BRL);
    }

    private static void assertBd(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
            "expected " + expected + " but was " + actual);
    }

    @Nested
    class PlaceOrder {

        @Test
        void noMatch_orderRestsInBook() {
            Order buy = buyOrder(1, "500", "1");
            engine.placeOrder(buy);

            OrderBook book = engine.getOrderBook(BTC_BRL);
            assertEquals(1, book.getBids().get(500L).size());
        }

        @Test
        void fullMatch_orderDoesNotRestInBook() {
            Order sell = sellOrder(1, "500", "1");
            engine.placeOrder(sell);

            Order buy = buyOrder(2, "500", "1");
            engine.placeOrder(buy);

            OrderBook book = engine.getOrderBook(BTC_BRL);
            assertTrue(book.getBids().isEmpty());
            assertTrue(book.getAsks().isEmpty());
        }

        @Test
        void partialMatch_remainderRestsInBook() {
            Order sell = sellOrder(1, "500", "0.5");
            engine.placeOrder(sell);

            Order buy = buyOrder(2, "500", "1");
            engine.placeOrder(buy);

            OrderBook book = engine.getOrderBook(BTC_BRL);
            assertEquals(1, book.getBids().get(500L).size());
            assertTrue(book.getAsks().isEmpty());
        }

        @Test
        void placeOrder_locksBuyerFunds() {
            Order buy = buyOrder(1, "500", "1");
            engine.placeOrder(buy);

            assertBd("500", buyer.getLockedBalance(Asset.BRL));
            assertBd("99500", buyer.getAvailableBalance(Asset.BRL));
        }

        @Test
        void placeOrder_locksSellerFunds() {
            Order sell = sellOrder(1, "500", "2");
            engine.placeOrder(sell);

            assertBd("2", seller.getLockedBalance(Asset.BTC));
            assertBd("8", seller.getAvailableBalance(Asset.BTC));
        }

        @Test
        void nullOrder_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> engine.placeOrder(null));
        }

        @Test
        void unknownInstrument_throwsIllegalArgumentException() {
            Instrument unknown = new Instrument(Asset.BTC, Asset.BRL);
            OrderBookEngine isolated = new OrderBookEngine(List.of(), Map.of(BUYER_ID, buyer));
            Order buy = buyOrder(1, "500", "1");
            assertThrows(IllegalArgumentException.class, () -> isolated.placeOrder(buy));
        }
    }

    @Nested
    class TradeSettlement {

        @Test
        void fullMatch_settlesBuyerAndSeller() {
            Order sell = sellOrder(1, "500", "1");
            engine.placeOrder(sell);

            Order buy = buyOrder(2, "500", "1");
            engine.placeOrder(buy);

            assertBd("1", buyer.getAvailableBalance(Asset.BTC));
            assertBd("9", seller.getAvailableBalance(Asset.BTC));
            assertBd("500", seller.getAvailableBalance(Asset.BRL));
        }

        @Test
        void fullMatch_tradeRecordedForBothAccounts() {
            Order sell = sellOrder(1, "500", "1");
            engine.placeOrder(sell);
            engine.placeOrder(buyOrder(2, "500", "1"));

            assertEquals(1, buyer.getTradeHistory().size());
            assertEquals(1, seller.getTradeHistory().size());
        }

        @Test
        void fullMatch_orderStatusSetToFilled() {
            Order sell = sellOrder(1, "500", "1");
            engine.placeOrder(sell);

            Order buy = buyOrder(2, "500", "1");
            engine.placeOrder(buy);

            assertEquals(OrderStatus.FILLED, sell.getStatus());
            assertEquals(OrderStatus.FILLED, buy.getStatus());
        }

        @Test
        void partialMatch_incomingStatusPartiallyFilled() {
            Order sell = sellOrder(1, "500", "0.3");
            engine.placeOrder(sell);

            Order buy = buyOrder(2, "500", "1");
            engine.placeOrder(buy);

            assertEquals(OrderStatus.FILLED, sell.getStatus());
            assertEquals(OrderStatus.PARTIALLY_FILLED, buy.getStatus());
        }

        @Test
        void multipleMatches_allTradesRecorded() {
            engine.placeOrder(sellOrder(1, "490", "0.5"));
            engine.placeOrder(sellOrder(2, "495", "0.5"));

            Order buy = buyOrder(3, "500", "1");
            engine.placeOrder(buy);

            assertEquals(2, buyer.getTradeHistory().size());
        }
    }

    @Nested
    class CancelOrder {

        @Test
        void cancelOpenOrder_removesFromBook() {
            Order buy = buyOrder(1, "500", "1");
            engine.placeOrder(buy);

            engine.cancelOrder(buy);

            OrderBook book = engine.getOrderBook(BTC_BRL);
            assertTrue(book.getBids().isEmpty());
            assertEquals(OrderStatus.CANCELED, buy.getStatus());
        }

        @Test
        void cancelOrder_unlocksSellerFunds() {
            Order sell = sellOrder(1, "500", "2");
            engine.placeOrder(sell);

            engine.cancelOrder(sell);

            assertBd("0", seller.getLockedBalance(Asset.BTC));
            assertBd("10", seller.getAvailableBalance(Asset.BTC));
        }

        @Test
        void cancelOrder_unlocksBuyerFunds() {
            Order buy = buyOrder(1, "500", "1");
            engine.placeOrder(buy);

            engine.cancelOrder(buy);

            assertBd("0", buyer.getLockedBalance(Asset.BRL));
            assertBd("100000", buyer.getAvailableBalance(Asset.BRL));
        }

        @Test
        void cancelPartiallyFilledOrder_unlocksRemainingFunds() {
            engine.placeOrder(sellOrder(1, "500", "0.3"));
            Order buy = buyOrder(2, "500", "1");
            engine.placeOrder(buy);

            assertEquals(OrderStatus.PARTIALLY_FILLED, buy.getStatus());
            engine.cancelOrder(buy);

            assertEquals(OrderStatus.CANCELED, buy.getStatus());
            assertBd("0", buyer.getLockedBalance(Asset.BRL));
        }

        @Test
        void cancelFilledOrder_throwsIllegalStateException() {
            Order sell = sellOrder(1, "500", "1");
            engine.placeOrder(sell);
            engine.placeOrder(buyOrder(2, "500", "1"));

            assertThrows(IllegalStateException.class, () -> engine.cancelOrder(sell));
        }

        @Test
        void cancelNullOrder_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> engine.cancelOrder(null));
        }
    }

    @Nested
    class GetOrderBook {

        @Test
        void returnsBookForKnownInstrument() {
            OrderBook book = engine.getOrderBook(BTC_BRL);
            assertEquals(BTC_BRL, book.getInstrument());
        }

        @Test
        void nullInstrument_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> engine.getOrderBook(null));
        }

        @Test
        void bidsOrderedDescending_bestBidFirst() {
            engine.placeOrder(buyOrder(1, "490", "1"));
            engine.placeOrder(buyOrder(2, "500", "1"));
            engine.placeOrder(buyOrder(3, "495", "1"));

            OrderBook book = engine.getOrderBook(BTC_BRL);
            long bestBid = book.getBids().firstKey();
            assertEquals(500L, bestBid);
        }

        @Test
        void asksOrderedAscending_bestAskFirst() {
            engine.placeOrder(sellOrder(1, "510", "1"));
            engine.placeOrder(sellOrder(2, "500", "1"));
            engine.placeOrder(sellOrder(3, "505", "1"));

            OrderBook book = engine.getOrderBook(BTC_BRL);
            long bestAsk = book.getAsks().firstKey();
            assertEquals(500L, bestAsk);
        }
    }
}
