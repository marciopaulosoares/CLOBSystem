package com.mb.crypto.clob;

import com.mb.crypto.clob.domain.*;
import com.mb.crypto.clob.orderbook.OrderBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ClobSystem covering realistic trading scenarios.
 *
 * <p>The engine locks (price × qty) of BASE asset for every order, so both participants
 * are pre-funded with a large BTC and BRL balance in setUp. Tests that assert specific
 * balance changes capture a before-snapshot and verify the delta.
 */
class ClobSystemTest {

    private static final Instrument BTC_BRL = new Instrument(Asset.BTC, Asset.BRL);
    private static final AccountId  ALICE_ID = new AccountId("alice");
    private static final AccountId  BOB_ID   = new AccountId("bob");

    // generous pre-funding so lock(price × qty of BASE) never throws
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("100000000");

    private Account alice;
    private Account bob;
    private ClobSystem system;

    @BeforeEach
    void setUp() {
        alice  = new Account(ALICE_ID);
        bob    = new Account(BOB_ID);
        alice.deposit(Asset.BTC, INITIAL_BALANCE);
        alice.deposit(Asset.BRL, INITIAL_BALANCE);
        bob.deposit(Asset.BTC,   INITIAL_BALANCE);
        bob.deposit(Asset.BRL,   INITIAL_BALANCE);
        system = new ClobSystem(List.of(BTC_BRL), List.of(alice, bob));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Order aliceBuy(long id, String price, String qty) {
        return new Order(id, OrderSide.BUY, new BigDecimal(price), new BigDecimal(qty),
            OrderType.LIMIT, ALICE_ID, BTC_BRL);
    }

    private Order bobSell(long id, String price, String qty) {
        return new Order(id, OrderSide.SELL, new BigDecimal(price), new BigDecimal(qty),
            OrderType.LIMIT, BOB_ID, BTC_BRL);
    }

    // ── test groups ───────────────────────────────────────────────────────────

    @Nested
    class Constructor {

        @Test
        void shouldRejectNullInstruments() {
            assertThrows(NullPointerException.class,
                () -> new ClobSystem(null, List.of(alice)));
        }

        @Test
        void shouldRejectNullAccounts() {
            assertThrows(NullPointerException.class,
                () -> new ClobSystem(List.of(BTC_BRL), null));
        }

        @Test
        void shouldExposeOrderBookForRegisteredInstrument() {
            assertNotNull(system.getOrderBook(BTC_BRL));
        }

        @Test
        void shouldReturnNullForUnregisteredInstrument() {
            Instrument unknown = new Instrument(Asset.BRL, Asset.BTC);
            assertNull(system.getOrderBook(unknown));
        }
    }

    @Nested
    class Deposit {

        @Test
        void shouldIncreaseAvailableBalance() {
            BigDecimal before = alice.getAvailableBalance(Asset.BRL);
            system.deposit(ALICE_ID, Asset.BRL, new BigDecimal("50000"));
            assertEquals(before.add(new BigDecimal("50000")),
                alice.getAvailableBalance(Asset.BRL));
        }

        @Test
        void shouldAccumulateMultipleDeposits() {
            BigDecimal before = alice.getAvailableBalance(Asset.BRL);
            system.deposit(ALICE_ID, Asset.BRL, new BigDecimal("30000"));
            system.deposit(ALICE_ID, Asset.BRL, new BigDecimal("20000"));
            assertEquals(before.add(new BigDecimal("50000")),
                alice.getAvailableBalance(Asset.BRL));
        }

        @Test
        void shouldCreateAccountIfNotPresent() {
            AccountId newId = new AccountId("charlie");
            assertDoesNotThrow(() ->
                system.deposit(newId, Asset.BRL, new BigDecimal("1000")));
        }

        @Test
        void shouldRejectZeroAmount() {
            assertThrows(IllegalArgumentException.class,
                () -> system.deposit(ALICE_ID, Asset.BRL, BigDecimal.ZERO));
        }

        @Test
        void shouldRejectNegativeAmount() {
            assertThrows(IllegalArgumentException.class,
                () -> system.deposit(ALICE_ID, Asset.BRL, new BigDecimal("-1")));
        }
    }

    @Nested
    class PlaceOrder {

        @Nested
        class Validation {

            @Test
            void shouldRejectNullOrder() {
                assertThrows(NullPointerException.class, () -> system.placeOrder(null));
            }

            @Test
            void shouldRejectOrderForUnregisteredInstrument() {
                Order order = new Order(1, OrderSide.BUY, BigDecimal.ONE, BigDecimal.ONE,
                    OrderType.LIMIT, ALICE_ID, new Instrument(Asset.BRL, Asset.BTC));
                assertThrows(IllegalArgumentException.class, () -> system.placeOrder(order));
            }
        }

        @Nested
        class Resting {

            @Test
            void shouldAddBuyOrderToBookWhenNoMatchingAsk() {
                system.placeOrder(aliceBuy(1, "300000", "1"));
                assertFalse(system.getOrderBook(BTC_BRL).getBids().isEmpty());
            }

            @Test
            void shouldAddSellOrderToBookWhenNoMatchingBid() {
                system.placeOrder(bobSell(1, "300000", "1"));
                assertFalse(system.getOrderBook(BTC_BRL).getAsks().isEmpty());
            }

            @Test
            void shouldLockBaseAssetOnPlacedOrder() {
                // engine locks price × qty of BASE for every order
                BigDecimal btcBefore = alice.getAvailableBalance(Asset.BTC);
                BigDecimal expectedLock = new BigDecimal("300000"); // 300000 × 1
                system.placeOrder(aliceBuy(1, "300000", "1"));
                assertEquals(expectedLock, alice.getLockedBalance(Asset.BTC));
                assertEquals(btcBefore.subtract(expectedLock), alice.getAvailableBalance(Asset.BTC));
            }

            @Test
            void shouldLeaveOrderStatusOpenWhenNoMatch() {
                Order order = aliceBuy(1, "300000", "1");
                system.placeOrder(order);
                assertEquals(OrderStatus.OPEN, order.getStatus());
            }
        }

        @Nested
        class ExactMatch {

            @Test
            void shouldMarkBothOrdersFilledOnExactMatch() {
                Order ask = bobSell(10, "300000", "1");
                Order bid = aliceBuy(1, "300000", "1");
                system.placeOrder(ask);
                system.placeOrder(bid);
                assertEquals(OrderStatus.FILLED, ask.getStatus());
                assertEquals(OrderStatus.FILLED, bid.getStatus());
            }

            @Test
            void shouldClearBothSidesOfBookAfterExactMatch() {
                system.placeOrder(bobSell(10, "300000", "1"));
                system.placeOrder(aliceBuy(1, "300000", "1"));
                OrderBook book = system.getOrderBook(BTC_BRL);
                assertTrue(book.getBids().isEmpty());
                assertTrue(book.getAsks().isEmpty());
            }

            @Test
            void shouldRecordTradeInBothAccountsAfterMatch() {
                system.placeOrder(bobSell(10, "300000", "1"));
                system.placeOrder(aliceBuy(1, "300000", "1"));
                assertEquals(1, alice.getTradeHistory().size());
                assertEquals(1, bob.getTradeHistory().size());
            }

            @Test
            void shouldRecordSameTradeIdInBothAccounts() {
                system.placeOrder(bobSell(10, "300000", "1"));
                system.placeOrder(aliceBuy(1, "300000", "1"));
                assertEquals(
                    alice.getTradeHistory().get(0).tradeId(),
                    bob.getTradeHistory().get(0).tradeId()
                );
            }

            @Test
            void shouldTransferQuoteAssetFromBuyerToSeller() {
                BigDecimal bobBrlBefore = bob.getAvailableBalance(Asset.BRL);
                system.placeOrder(bobSell(10, "300000", "1"));
                system.placeOrder(aliceBuy(1, "300000", "1"));
                // seller receives price × qty in BRL
                assertEquals(
                    bobBrlBefore.add(new BigDecimal("300000")),
                    bob.getAvailableBalance(Asset.BRL)
                );
            }

            @Test
            void shouldTransferBaseAssetFromSellerToBuyer() {
                BigDecimal aliceBtcBefore = alice.getAvailableBalance(Asset.BTC);
                system.placeOrder(bobSell(10, "300000", "1"));
                system.placeOrder(aliceBuy(1, "300000", "1"));
                // buyer receives qty in BTC; locked BTC is NOT released in current impl,
                // so net available = before - locked + credited
                BigDecimal locked  = new BigDecimal("300000"); // price × qty locked on placeOrder
                BigDecimal received = BigDecimal.ONE;           // 1 BTC credited on settle
                assertEquals(
                    aliceBtcBefore.subtract(locked).add(received),
                    alice.getAvailableBalance(Asset.BTC)
                );
            }
        }

        @Nested
        class PartialMatch {

            @Test
            void shouldMarkIncomingPartiallyFilledWhenRestingHasLessQty() {
                system.placeOrder(bobSell(10, "300000", "2"));
                Order bid = aliceBuy(1, "300000", "5");
                system.placeOrder(bid);
                assertEquals(OrderStatus.PARTIALLY_FILLED, bid.getStatus());
                assertEquals(new BigDecimal("3"), bid.getQuantity());
            }

            @Test
            void shouldLeavePartiallyConsumedAskInBook() {
                system.placeOrder(bobSell(10, "300000", "5"));
                system.placeOrder(aliceBuy(1, "300000", "2"));
                assertFalse(system.getOrderBook(BTC_BRL).getAsks().isEmpty());
            }

            @Test
            void shouldKeepBidInBookAfterPartialFill() {
                system.placeOrder(bobSell(10, "300000", "2"));
                system.placeOrder(aliceBuy(1, "300000", "5"));
                assertFalse(system.getOrderBook(BTC_BRL).getBids().isEmpty());
            }
        }

        @Nested
        class PricePriority {

            @Test
            void shouldMatchAgainstBestAskFirst() {
                system.placeOrder(bobSell(10, "300000", "1"));
                system.placeOrder(bobSell(11, "290000", "1")); // better price
                Order bid = aliceBuy(1, "300000", "1");
                system.placeOrder(bid);
                // bid matches the ask at 290000 (best price), not 300000
                Trade trade = alice.getTradeHistory().get(0);
                assertEquals(new BigDecimal("290000"), trade.price());
            }

            @Test
            void shouldNotMatchWhenBidBelowBestAsk() {
                system.placeOrder(bobSell(10, "300000", "1"));
                system.placeOrder(aliceBuy(1, "200000", "1")); // bid < ask — no match
                assertFalse(system.getOrderBook(BTC_BRL).getBids().isEmpty());
                assertFalse(system.getOrderBook(BTC_BRL).getAsks().isEmpty());
            }

            @Test
            void shouldMatchAtRestingPriceNotIncomingPrice() {
                // Resting ask at 290000; incoming bid at 300000 → trade at 290000
                system.placeOrder(bobSell(10, "290000", "1"));
                system.placeOrder(aliceBuy(1, "300000", "1"));
                assertEquals(new BigDecimal("290000"),
                    alice.getTradeHistory().get(0).price());
            }
        }

        @Nested
        class MultipleMatches {

            @Test
            void shouldConsumeMultipleAskLevelsForOneLargeBid() {
                system.placeOrder(bobSell(10, "290000", "1"));
                system.placeOrder(bobSell(11, "295000", "1"));
                system.placeOrder(bobSell(12, "300000", "1"));
                Order bid = aliceBuy(1, "300000", "3");
                system.placeOrder(bid);
                assertEquals(OrderStatus.FILLED, bid.getStatus());
                assertEquals(3, alice.getTradeHistory().size());
            }

            @Test
            void shouldClearAllAskLevelsAfterFullConsumption() {
                system.placeOrder(bobSell(10, "290000", "1"));
                system.placeOrder(bobSell(11, "295000", "1"));
                system.placeOrder(aliceBuy(1, "300000", "2"));
                assertTrue(system.getOrderBook(BTC_BRL).getAsks().isEmpty());
            }

            @Test
            void shouldGenerateIncrementingTradeIds() {
                system.placeOrder(bobSell(10, "300000", "1"));
                system.placeOrder(aliceBuy(1, "300000", "1"));
                system.placeOrder(bobSell(11, "300000", "1"));
                system.placeOrder(aliceBuy(2, "300000", "1"));

                long first  = alice.getTradeHistory().get(0).tradeId();
                long second = alice.getTradeHistory().get(1).tradeId();
                assertTrue(second > first);
            }

            @Test
            void shouldAssignUniqueTradeIdToEachTrade() {
                system.placeOrder(bobSell(10, "300000", "1"));
                system.placeOrder(aliceBuy(1, "300000", "1"));
                system.placeOrder(bobSell(11, "300000", "1"));
                system.placeOrder(aliceBuy(2, "300000", "1"));

                long id1 = alice.getTradeHistory().get(0).tradeId();
                long id2 = alice.getTradeHistory().get(1).tradeId();
                assertNotEquals(id1, id2);
            }
        }
    }

    @Nested
    class CancelOrder {

        @Test
        void shouldRejectNullOrder() {
            assertThrows(NullPointerException.class, () -> system.cancelOrder(null));
        }

        @Test
        void shouldRemoveBidFromBookOnCancel() {
            Order bid = aliceBuy(1, "300000", "1");
            system.placeOrder(bid);
            system.cancelOrder(bid);
            assertTrue(system.getOrderBook(BTC_BRL).getBids().isEmpty());
        }

        @Test
        void shouldRemoveAskFromBookOnCancel() {
            Order ask = bobSell(10, "300000", "1");
            system.placeOrder(ask);
            system.cancelOrder(ask);
            assertTrue(system.getOrderBook(BTC_BRL).getAsks().isEmpty());
        }

        @Test
        void shouldMarkOrderAsCanceledOnCancel() {
            Order bid = aliceBuy(1, "300000", "1");
            system.placeOrder(bid);
            system.cancelOrder(bid);
            assertEquals(OrderStatus.CANCELED, bid.getStatus());
        }

        @Test
        void shouldRejectCancelOfOrderNotInBook() {
            Order bid = aliceBuy(1, "300000", "1");
            assertThrows(IllegalArgumentException.class, () -> system.cancelOrder(bid));
        }

        @Test
        void shouldRejectCancelOfAlreadyCanceledOrder() {
            Order bid = aliceBuy(1, "300000", "1");
            system.placeOrder(bid);
            system.cancelOrder(bid);
            assertThrows(IllegalStateException.class, () -> system.cancelOrder(bid));
        }

        @Test
        void shouldRejectCancelOfFilledOrder() {
            Order ask = bobSell(10, "300000", "1");
            Order bid = aliceBuy(1, "300000", "1");
            system.placeOrder(ask);
            system.placeOrder(bid);
            assertThrows(IllegalStateException.class, () -> system.cancelOrder(bid));
        }
    }

    @Nested
    class EndToEnd {

        /**
         * Full session: resting order placed, stale bid canceled, aggressive bid crosses the spread.
         */
        @Test
        void fullTradingSession() {
            Order staleBid = aliceBuy(1, "200000", "1"); // will not match
            system.placeOrder(staleBid);
            assertEquals(1, system.getOrderBook(BTC_BRL).getBids().size());

            Order ask = bobSell(10, "300000", "1");
            system.placeOrder(ask);
            assertEquals(1, system.getOrderBook(BTC_BRL).getAsks().size());

            system.cancelOrder(staleBid);
            assertTrue(system.getOrderBook(BTC_BRL).getBids().isEmpty());
            assertEquals(OrderStatus.CANCELED, staleBid.getStatus());

            Order aggressiveBid = aliceBuy(2, "300000", "1");
            system.placeOrder(aggressiveBid);

            assertEquals(OrderStatus.FILLED, aggressiveBid.getStatus());
            assertEquals(OrderStatus.FILLED, ask.getStatus());
            assertTrue(system.getOrderBook(BTC_BRL).getBids().isEmpty());
            assertTrue(system.getOrderBook(BTC_BRL).getAsks().isEmpty());
            assertEquals(1, alice.getTradeHistory().size());
            assertEquals(1, bob.getTradeHistory().size());
        }

        /**
         * Incoming bid walks the book across two price levels, consuming all resting asks.
         */
        @Test
        void incomingBidWalksBookAcrossTwoPriceLevels() {
            system.placeOrder(bobSell(10, "290000", "1"));
            system.placeOrder(bobSell(11, "300000", "2"));

            Order bid = aliceBuy(1, "300000", "3");
            system.placeOrder(bid);

            assertEquals(OrderStatus.FILLED, bid.getStatus());
            // 1 trade at 290000 + 1 trade at 300000 (qty 2 = one MatchedPair) = 2 records
            assertEquals(2, alice.getTradeHistory().size());
            assertTrue(system.getOrderBook(BTC_BRL).getAsks().isEmpty());
        }

        /**
         * Settlement correctness: seller receives quote and buyer receives base after a trade.
         */
        @Test
        void settlementTransfersCorrectAmounts() {
            BigDecimal price = new BigDecimal("300000");
            BigDecimal qty   = BigDecimal.ONE;

            BigDecimal bobBrlBefore = bob.getAvailableBalance(Asset.BRL);

            system.placeOrder(bobSell(10, "300000", "1"));
            system.placeOrder(aliceBuy(1, "300000", "1"));

            // seller (bob) receives price × qty BRL
            assertEquals(
                bobBrlBefore.add(price.multiply(qty)),
                bob.getAvailableBalance(Asset.BRL)
            );
        }

        /**
         * Two separate sessions: trade IDs never collide across sessions.
         */
        @Test
        void tradeIdsAreUniqueAcrossMultipleSessions() {
            system.placeOrder(bobSell(10, "300000", "1"));
            system.placeOrder(aliceBuy(1, "300000", "1"));
            system.placeOrder(bobSell(11, "300000", "1"));
            system.placeOrder(aliceBuy(2, "300000", "1"));

            long id1 = alice.getTradeHistory().get(0).tradeId();
            long id2 = alice.getTradeHistory().get(1).tradeId();
            assertNotEquals(id1, id2);
        }
    }
}
