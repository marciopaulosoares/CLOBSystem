package com.mb.crypto.clob.matching;

import com.mb.crypto.clob.domain.*;
import com.mb.crypto.clob.orderbook.OrderBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookEngineTest {

    private static final Instrument BTC_BRL  = new Instrument(Asset.BTC, Asset.BRL);
    private static final AccountId  BUYER_ID  = new AccountId("buyer");
    private static final AccountId  SELLER_ID = new AccountId("seller");

    private Account buyer;
    private Account seller;
    private Map<AccountId, Account> accounts;
    private OrderBookEngine engine;

    @BeforeEach
    void setUp() {
        buyer  = new Account(BUYER_ID);
        seller = new Account(SELLER_ID);
        accounts = new HashMap<>();
        accounts.put(BUYER_ID,  buyer);
        accounts.put(SELLER_ID, seller);
        engine = new OrderBookEngine(List.of(BTC_BRL), accounts);

        // fund both accounts so placeOrder lock (price × qty BTC) does not throw
        buyer.deposit(Asset.BTC,  new BigDecimal("10000000"));
        seller.deposit(Asset.BTC, new BigDecimal("10000000"));
        // buyer settle debits BRL; deposit ensures the balance entry exists in the map
        buyer.deposit(Asset.BRL,  new BigDecimal("10000000"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Order buyOrder(long id, String price, String qty) {
        return new Order(id, OrderSide.BUY,  new BigDecimal(price), new BigDecimal(qty),
            OrderType.LIMIT, BUYER_ID,  BTC_BRL);
    }

    private Order sellOrder(long id, String price, String qty) {
        return new Order(id, OrderSide.SELL, new BigDecimal(price), new BigDecimal(qty),
            OrderType.LIMIT, SELLER_ID, BTC_BRL);
    }

    // ── test groups ───────────────────────────────────────────────────────────

    @Nested
    class Constructor {

        @Test
        void shouldRejectNullInstruments() {
            assertThrows(NullPointerException.class,
                () -> new OrderBookEngine(null, accounts));
        }

        @Test
        void shouldRejectNullAccounts() {
            assertThrows(NullPointerException.class,
                () -> new OrderBookEngine(List.of(BTC_BRL), null));
        }

        @Test
        void shouldCreateOneOrderBookPerInstrument() {
            assertNotNull(engine.getOrderBook(BTC_BRL));
        }

        @Test
        void shouldReturnNullForUnregisteredInstrument() {
            Instrument other = new Instrument(Asset.BRL, Asset.BTC);
            assertNull(engine.getOrderBook(other));
        }
    }

    @Nested
    class GetOrderBook {

        @Test
        void shouldRejectNullInstrument() {
            assertThrows(NullPointerException.class,
                () -> engine.getOrderBook(null));
        }

        @Test
        void shouldReturnOrderBookForRegisteredInstrument() {
            OrderBook book = engine.getOrderBook(BTC_BRL);
            assertNotNull(book);
            assertEquals(BTC_BRL, book.getInstrument());
        }

        @Test
        void shouldReturnNullForUnregisteredInstrument() {
            assertNull(engine.getOrderBook(new Instrument(Asset.BRL, Asset.BTC)));
        }
    }

    @Nested
    class PlaceOrder {

        @Nested
        class Validation {

            @Test
            void shouldRejectNullOrder() {
                assertThrows(NullPointerException.class, () -> engine.placeOrder(null));
            }

            @Test
            void shouldRejectCanceledOrder() {
                Order order = buyOrder(1, "1", "1");
                order.cancel();
                assertThrows(IllegalStateException.class, () -> engine.placeOrder(order));
            }

            @Test
            void shouldRejectFilledOrder() {
                Order order = buyOrder(1, "1", "1");
                order.decreaseQuantity(new BigDecimal("1")); // qty → 0
                order.applyFill();                           // qty=0 → FILLED
                assertThrows(IllegalStateException.class, () -> engine.placeOrder(order));
            }
        }

        @Nested
        class NoMatch {

            @Test
            void shouldAddBuyOrderToBidsWhenNoMatchingAsk() {
                engine.placeOrder(buyOrder(1, "150000", "1"));
                assertFalse(engine.getOrderBook(BTC_BRL).getBids().isEmpty());
            }

            @Test
            void shouldAddSellOrderToAsksWhenNoMatchingBid() {
                engine.placeOrder(sellOrder(1, "150000", "1"));
                assertFalse(engine.getOrderBook(BTC_BRL).getAsks().isEmpty());
            }

            @Test
            void shouldLeaveOrderStatusOpenWhenNoMatch() {
                Order order = buyOrder(1, "150000", "1");
                engine.placeOrder(order);
                assertEquals(OrderStatus.OPEN, order.getStatus());
            }

            @Test
            void shouldNotAddBuyToBidsAfterFullMatch() {
                engine.placeOrder(sellOrder(10, "150000", "1"));
                engine.placeOrder(buyOrder(1,  "150000", "1"));
                assertTrue(engine.getOrderBook(BTC_BRL).getBids().isEmpty());
                assertTrue(engine.getOrderBook(BTC_BRL).getAsks().isEmpty());
            }
        }

        @Nested
        class WithMatch {

            @Test
            void shouldMarkBothOrdersFilledOnExactMatch() {
                Order ask = sellOrder(10, "1", "1");
                Order bid = buyOrder(1,  "1", "1");
                engine.placeOrder(ask);
                engine.placeOrder(bid);
                assertEquals(OrderStatus.FILLED, ask.getStatus());
                assertEquals(OrderStatus.FILLED, bid.getStatus());
            }

            @Test
            void shouldReduceQuantitiesToZeroOnExactMatch() {
                Order ask = sellOrder(10, "1", "1");
                Order bid = buyOrder(1,  "1", "1");
                engine.placeOrder(ask);
                engine.placeOrder(bid);
                assertEquals(new BigDecimal("0"), ask.getQuantity());
                assertEquals(new BigDecimal("0"), bid.getQuantity());
            }

            @Test
            void shouldRecordTradeInBothAccountsOnMatch() {
                engine.placeOrder(sellOrder(10, "1", "1"));
                engine.placeOrder(buyOrder(1,  "1", "1"));
                assertEquals(1, seller.getTradeHistory().size());
                assertEquals(1, buyer.getTradeHistory().size());
            }

            @Test
            void shouldAssignSameTradeRecordToBothAccounts() {
                engine.placeOrder(sellOrder(10, "1", "1"));
                engine.placeOrder(buyOrder(1,  "1", "1"));
                assertEquals(
                    seller.getTradeHistory().get(0).tradeId(),
                    buyer.getTradeHistory().get(0).tradeId()
                );
            }

            @Test
            void shouldMarkIncomingAsPartiallyFilledWhenRestingHasLessQty() {
                engine.placeOrder(sellOrder(10, "1", "2"));
                Order bid = buyOrder(1, "1", "5");
                engine.placeOrder(bid);
                assertEquals(OrderStatus.PARTIALLY_FILLED, bid.getStatus());
                assertEquals(new BigDecimal("3"), bid.getQuantity());
            }

            @Test
            void shouldLeaveRestingOrderInBookAfterPartialConsumption() {
                engine.placeOrder(sellOrder(10, "1", "5"));
                engine.placeOrder(buyOrder(1, "1", "2"));
                assertFalse(engine.getOrderBook(BTC_BRL).getAsks().isEmpty());
            }
        }
    }

    @Nested
    class CancelOrder {

        @Nested
        class Validation {

            @Test
            void shouldRejectNullOrder() {
                assertThrows(NullPointerException.class, () -> engine.cancelOrder(null));
            }

            @Test
            void shouldRejectCanceledOrder() {
                Order order = buyOrder(1, "1", "1");
                order.cancel();
                assertThrows(IllegalStateException.class, () -> engine.cancelOrder(order));
            }

            @Test
            void shouldRejectFilledOrder() {
                Order order = buyOrder(1, "1", "1");
                order.decreaseQuantity(new BigDecimal("1")); // qty → 0
                order.applyFill();                           // qty=0 → FILLED
                assertThrows(IllegalStateException.class, () -> engine.cancelOrder(order));
            }

            @Test
            void shouldRejectUnknownInstrument() {
                Order order = new Order(1, OrderSide.BUY, BigDecimal.ONE, BigDecimal.ONE,
                    OrderType.LIMIT, BUYER_ID, new Instrument(Asset.BRL, Asset.BTC));
                assertThrows(IllegalArgumentException.class, () -> engine.cancelOrder(order));
            }
        }

        @Nested
        class HappyPath {

            @Test
            void shouldRemoveOrderFromBookOnCancel() {
                Order order = buyOrder(1, "150000", "1");
                engine.placeOrder(order);
                assertFalse(engine.getOrderBook(BTC_BRL).getBids().isEmpty());
                engine.cancelOrder(order);
                assertTrue(engine.getOrderBook(BTC_BRL).getBids().isEmpty());
            }

            @Test
            void shouldMarkOrderAsCanceledOnCancel() {
                Order order = buyOrder(1, "150000", "1");
                engine.placeOrder(order);
                engine.cancelOrder(order);
                assertEquals(OrderStatus.CANCELED, order.getStatus());
            }

            @Test
            void shouldRejectCancelOfOrderNotInBook() {
                Order order = buyOrder(1, "150000", "1");
                assertThrows(IllegalArgumentException.class, () -> engine.cancelOrder(order));
            }
        }
    }

    @Nested
    class TradeIdSequence {

        @Test
        void shouldAssignIncrementingTradeIds() {
            engine.placeOrder(sellOrder(10, "1", "1"));
            engine.placeOrder(buyOrder(1,  "1", "1"));
            engine.placeOrder(sellOrder(11, "1", "1"));
            engine.placeOrder(buyOrder(2,  "1", "1"));

            long firstTradeId  = buyer.getTradeHistory().get(0).tradeId();
            long secondTradeId = buyer.getTradeHistory().get(1).tradeId();
            assertTrue(secondTradeId > firstTradeId);
        }

        @Test
        void shouldAssignUniqueTradeIdPerTrade() {
            engine.placeOrder(sellOrder(10, "1", "1"));
            engine.placeOrder(buyOrder(1,  "1", "1"));
            engine.placeOrder(sellOrder(11, "1", "1"));
            engine.placeOrder(buyOrder(2,  "1", "1"));

            long id1 = buyer.getTradeHistory().get(0).tradeId();
            long id2 = buyer.getTradeHistory().get(1).tradeId();
            assertNotEquals(id1, id2);
        }
    }
}
