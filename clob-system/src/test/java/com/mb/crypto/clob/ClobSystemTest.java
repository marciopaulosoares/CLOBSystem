package com.mb.crypto.clob;

import com.mb.crypto.clob.domain.Account;
import com.mb.crypto.clob.domain.AccountId;
import com.mb.crypto.clob.domain.Asset;
import com.mb.crypto.clob.domain.Instrument;
import com.mb.crypto.clob.domain.Order;
import com.mb.crypto.clob.domain.OrderSide;
import com.mb.crypto.clob.domain.OrderStatus;
import com.mb.crypto.clob.domain.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// BigDecimal.assertEquals is scale-sensitive (500 ≠ 500.00000000).
// Use assertBd for value-only comparisons throughout this file.

class ClobSystemTest {

    private static final Instrument INSTRUMENT = new Instrument(Asset.BTC, Asset.BRL);

    private static final AccountId BUYER_ID  = new AccountId("buyer");
    private static final AccountId SELLER_ID = new AccountId("seller");

    // 1 BTC = 100_000_000 satoshis
    private static final long QTY_1_BTC = 100_000_000L;
    private static final long QTY_2_BTC = 200_000_000L;

    private Account buyer;
    private Account seller;
    private ClobSystem system;

    private static void assertBd(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual),
            () -> "expected: <" + expected.toPlainString() + "> but was: <" + actual.toPlainString() + ">");
    }

    private Order buyOrder(long id, long price, long qty) {
        return new Order(id, OrderSide.BUY, price, qty, OrderType.LIMIT, BUYER_ID, INSTRUMENT);
    }

    private Order sellOrder(long id, long price, long qty) {
        return new Order(id, OrderSide.SELL, price, qty, OrderType.LIMIT, SELLER_ID, INSTRUMENT);
    }

    @BeforeEach
    void setUp() {
        buyer  = new Account(BUYER_ID);
        seller = new Account(SELLER_ID);
        system = new ClobSystem(List.of(INSTRUMENT), List.of(buyer, seller));
    }

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    @Nested
    class Constructor {

        @Test
        void shouldRejectNullInstruments() {
            assertThrows(NullPointerException.class,
                () -> new ClobSystem(null, List.of()));
        }

        @Test
        void shouldRejectNullAccounts() {
            assertThrows(NullPointerException.class,
                () -> new ClobSystem(List.of(INSTRUMENT), null));
        }

        @Test
        void shouldCreateOrderBookForEachInstrument() {
            assertNotNull(system.getOrderBook(INSTRUMENT));
        }
    }

    // -------------------------------------------------------------------------
    // placeOrder
    // -------------------------------------------------------------------------

    @Nested
    class PlaceOrder {

        @Test
        void shouldRejectNullOrder() {
            assertThrows(NullPointerException.class, () -> system.placeOrder(null));
        }

        @Test
        void shouldRejectOrderForUnknownInstrument() {
            Instrument other = new Instrument(Asset.BRL, Asset.BTC);
            new ClobSystem(List.of(other), List.of(buyer));  // other system, not our book

            Order buy = new Order(1, OrderSide.BUY, 500L, QTY_1_BTC, OrderType.LIMIT,
                BUYER_ID, other);
            // "other" is not registered in the default system
            assertThrows(IllegalArgumentException.class,
                () -> system.placeOrder(buy));
        }

        @Nested
        class NoMatch {

            @Test
            void buyWithNoResting_restsInBook() {
                buyer.deposit(Asset.BRL, new BigDecimal("500"));
                Order buy = buyOrder(1, 500L, QTY_1_BTC);

                system.placeOrder(buy);

                assertFalse(system.getOrderBook(INSTRUMENT).getBids().isEmpty());
            }

            @Test
            void buyWithNoResting_buyerFundsAreLocked() {
                buyer.deposit(Asset.BRL, new BigDecimal("500"));
                Order buy = buyOrder(1, 500L, QTY_1_BTC);

                system.placeOrder(buy);

                // notional = 500 BRL * 1 BTC = 500 BRL locked
                assertBd(new BigDecimal("500"), buyer.getLockedBalance(Asset.BRL));
            }

            @Test
            void sellWithNoResting_restsInBook() {
                seller.deposit(Asset.BTC, new BigDecimal("1"));
                Order sell = sellOrder(1, 500L, QTY_1_BTC);

                system.placeOrder(sell);

                assertFalse(system.getOrderBook(INSTRUMENT).getAsks().isEmpty());
            }

            @Test
            void sellWithNoResting_sellerBtcIsLocked() {
                seller.deposit(Asset.BTC, new BigDecimal("1"));
                Order sell = sellOrder(1, 500L, QTY_1_BTC);

                system.placeOrder(sell);

                assertBd(new BigDecimal("1"), seller.getLockedBalance(Asset.BTC));
            }
        }

        @Nested
        class FullMatch {

            @BeforeEach
            void fundAccounts() {
                buyer.deposit(Asset.BRL, new BigDecimal("500"));
                seller.deposit(Asset.BTC, new BigDecimal("1"));
            }

            @Test
            void buyMatchesSell_orderBookIsEmpty() {
                system.placeOrder(sellOrder(1, 500L, QTY_1_BTC));
                system.placeOrder(buyOrder(2, 500L, QTY_1_BTC));

                assertTrue(system.getOrderBook(INSTRUMENT).getBids().isEmpty());
                assertTrue(system.getOrderBook(INSTRUMENT).getAsks().isEmpty());
            }

            @Test
            void buyMatchesSell_buyerReceivesBtc() {
                system.placeOrder(sellOrder(1, 500L, QTY_1_BTC));
                system.placeOrder(buyOrder(2, 500L, QTY_1_BTC));

                assertBd(new BigDecimal("1"), buyer.getBalance(Asset.BTC));
            }

            @Test
            void buyMatchesSell_sellerReceivesBrl() {
                system.placeOrder(sellOrder(1, 500L, QTY_1_BTC));
                system.placeOrder(buyOrder(2, 500L, QTY_1_BTC));

                // seller sold 1 BTC at 500 BRL → receives 500 BRL
                assertBd(new BigDecimal("500"), seller.getBalance(Asset.BRL));
            }

            @Test
            void buyMatchesSell_buyerBrlLockedIsReleasedToZero() {
                system.placeOrder(sellOrder(1, 500L, QTY_1_BTC));
                system.placeOrder(buyOrder(2, 500L, QTY_1_BTC));

                assertBd(BigDecimal.ZERO, buyer.getLockedBalance(Asset.BRL));
            }

            @Test
            void buyMatchesSell_sellerBtcLockedIsReleasedToZero() {
                system.placeOrder(sellOrder(1, 500L, QTY_1_BTC));
                system.placeOrder(buyOrder(2, 500L, QTY_1_BTC));

                assertBd(BigDecimal.ZERO, seller.getLockedBalance(Asset.BTC));
            }

            @Test
            void buyMatchesSell_incomingOrderStatusIsFilled() {
                system.placeOrder(sellOrder(1, 500L, QTY_1_BTC));
                Order buy = buyOrder(2, 500L, QTY_1_BTC);
                system.placeOrder(buy);

                assertEquals(OrderStatus.FILLED, buy.getStatus());
            }

            @Test
            void buyMatchesSell_restingOrderStatusIsFilled() {
                Order sell = sellOrder(1, 500L, QTY_1_BTC);
                system.placeOrder(sell);
                system.placeOrder(buyOrder(2, 500L, QTY_1_BTC));

                assertEquals(OrderStatus.FILLED, sell.getStatus());
            }

            @Test
            void tradeRecordedInBuyerHistory() {
                system.placeOrder(sellOrder(1, 500L, QTY_1_BTC));
                system.placeOrder(buyOrder(2, 500L, QTY_1_BTC));

                assertEquals(1, buyer.getTradeHistory().size());
            }

            @Test
            void tradeRecordedInSellerHistory() {
                system.placeOrder(sellOrder(1, 500L, QTY_1_BTC));
                system.placeOrder(buyOrder(2, 500L, QTY_1_BTC));

                assertEquals(1, seller.getTradeHistory().size());
            }
        }

        @Nested
        class PartialMatch {

            @BeforeEach
            void fundAccounts() {
                buyer.deposit(Asset.BRL, new BigDecimal("1000"));   // enough for 2 BTC
                seller.deposit(Asset.BTC, new BigDecimal("1"));     // only 1 BTC
            }

            @Test
            void buyLargerThanResting_remainderRestsInBook() {
                system.placeOrder(sellOrder(1, 500L, QTY_1_BTC));   // 1 BTC resting
                system.placeOrder(buyOrder(2, 500L, QTY_2_BTC));    // 2 BTC incoming

                assertFalse(system.getOrderBook(INSTRUMENT).getBids().isEmpty());
            }

            @Test
            void buyLargerThanResting_incomingOrderIsPartiallyFilled() {
                system.placeOrder(sellOrder(1, 500L, QTY_1_BTC));
                Order buy = buyOrder(2, 500L, QTY_2_BTC);
                system.placeOrder(buy);

                assertEquals(OrderStatus.PARTIALLY_FILLED, buy.getStatus());
            }

            @Test
            void buyLargerThanResting_restingOrderIsFullyFilled() {
                Order sell = sellOrder(1, 500L, QTY_1_BTC);
                system.placeOrder(sell);
                system.placeOrder(buyOrder(2, 500L, QTY_2_BTC));

                assertEquals(OrderStatus.FILLED, sell.getStatus());
            }

            @Test
            void buyLargerThanResting_buyerReceivesPartialBtc() {
                system.placeOrder(sellOrder(1, 500L, QTY_1_BTC));
                system.placeOrder(buyOrder(2, 500L, QTY_2_BTC));

                assertBd(new BigDecimal("1"), buyer.getBalance(Asset.BTC));
            }
        }
    }

    // -------------------------------------------------------------------------
    // cancelOrder
    // -------------------------------------------------------------------------

    @Nested
    class CancelOrder {

        @Test
        void shouldRejectNullOrder() {
            assertThrows(NullPointerException.class, () -> system.cancelOrder(null));
        }

        @Test
        void cancelRestingBuy_orderRemovedFromBook() {
            buyer.deposit(Asset.BRL, new BigDecimal("500"));
            Order buy = buyOrder(1, 500L, QTY_1_BTC);
            system.placeOrder(buy);

            system.cancelOrder(buy);

            assertTrue(system.getOrderBook(INSTRUMENT).getBids().isEmpty());
        }

        @Test
        void cancelRestingBuy_orderStatusIsCanceled() {
            buyer.deposit(Asset.BRL, new BigDecimal("500"));
            Order buy = buyOrder(1, 500L, QTY_1_BTC);
            system.placeOrder(buy);

            system.cancelOrder(buy);

            assertEquals(OrderStatus.CANCELED, buy.getStatus());
        }

        @Test
        void cancelRestingBuy_buyerFundsAreUnlocked() {
            buyer.deposit(Asset.BRL, new BigDecimal("500"));
            Order buy = buyOrder(1, 500L, QTY_1_BTC);
            system.placeOrder(buy);

            system.cancelOrder(buy);

            assertBd(BigDecimal.ZERO, buyer.getLockedBalance(Asset.BRL));
            assertBd(new BigDecimal("500"), buyer.getAvailableBalance(Asset.BRL));
        }

        @Test
        void cancelRestingSell_orderRemovedFromBook() {
            seller.deposit(Asset.BTC, new BigDecimal("1"));
            Order sell = sellOrder(1, 500L, QTY_1_BTC);
            system.placeOrder(sell);

            system.cancelOrder(sell);

            assertTrue(system.getOrderBook(INSTRUMENT).getAsks().isEmpty());
        }

        @Test
        void cancelRestingSell_sellerBtcIsUnlocked() {
            seller.deposit(Asset.BTC, new BigDecimal("1"));
            Order sell = sellOrder(1, 500L, QTY_1_BTC);
            system.placeOrder(sell);

            system.cancelOrder(sell);

            assertBd(BigDecimal.ZERO, seller.getLockedBalance(Asset.BTC));
            assertBd(new BigDecimal("1"), seller.getAvailableBalance(Asset.BTC));
        }

        @Test
        void cancelOrderNotInBook_throws() {
            Order buy = buyOrder(99, 500L, QTY_1_BTC);
            // never placed — not in book
            assertThrows(Exception.class, () -> system.cancelOrder(buy));
        }
    }

    // -------------------------------------------------------------------------
    // addAccount
    // -------------------------------------------------------------------------

    @Nested
    class AddAccount {

        @Test
        void shouldRejectNullAccount() {
            assertThrows(NullPointerException.class, () -> system.addAccount(null));
        }

        @Test
        void addNewAccount_allowsSubsequentDeposit() {
            AccountId newId = new AccountId("newcomer");
            system.addAccount(new Account(newId));
            system.deposit(newId, Asset.BRL, new BigDecimal("100"));

            // no exception means the account was found and the deposit succeeded
        }

        @Test
        void addDuplicateAccount_doesNotOverwriteExisting() {
            buyer.deposit(Asset.BRL, new BigDecimal("100"));

            // attempt to replace buyer with a fresh empty account
            system.addAccount(new Account(BUYER_ID));

            // original buyer's balance must still be there
            assertEquals(new BigDecimal("100"), buyer.getAvailableBalance(Asset.BRL));
        }
    }

    // -------------------------------------------------------------------------
    // getOrderBook
    // -------------------------------------------------------------------------

    @Nested
    class GetOrderBook {

        @Test
        void shouldRejectNullInstrument() {
            assertThrows(NullPointerException.class, () -> system.getOrderBook(null));
        }

        @Test
        void returnsOrderBookForKnownInstrument() {
            assertNotNull(system.getOrderBook(INSTRUMENT));
        }

        @Test
        void returnsNullForUnknownInstrument() {
            Instrument unknown = new Instrument(Asset.BRL, Asset.BTC);
            assertNull(system.getOrderBook(unknown));
        }
    }

    // -------------------------------------------------------------------------
    // deposit
    // -------------------------------------------------------------------------

    @Nested
    class Deposit {

        @Test
        void shouldRejectNullAccountId() {
            assertThrows(NullPointerException.class,
                () -> system.deposit(null, Asset.BRL, BigDecimal.TEN));
        }

        @Test
        void shouldRejectNullAsset() {
            assertThrows(NullPointerException.class,
                () -> system.deposit(BUYER_ID, null, BigDecimal.TEN));
        }

        @Test
        void shouldRejectNullAmount() {
            assertThrows(NullPointerException.class,
                () -> system.deposit(BUYER_ID, Asset.BRL, null));
        }

        @Test
        void shouldRejectZeroAmount() {
            assertThrows(IllegalArgumentException.class,
                () -> system.deposit(BUYER_ID, Asset.BRL, BigDecimal.ZERO));
        }

        @Test
        void shouldRejectNegativeAmount() {
            assertThrows(IllegalArgumentException.class,
                () -> system.deposit(BUYER_ID, Asset.BRL, new BigDecimal("-1")));
        }

        @Test
        void depositIncreasesAvailableBalance() {
            system.deposit(BUYER_ID, Asset.BRL, new BigDecimal("250"));

            assertEquals(new BigDecimal("250"), buyer.getAvailableBalance(Asset.BRL));
        }

        @Test
        void multipleDepositsAccumulate() {
            system.deposit(BUYER_ID, Asset.BRL, new BigDecimal("100"));
            system.deposit(BUYER_ID, Asset.BRL, new BigDecimal("50"));

            assertEquals(new BigDecimal("150"), buyer.getAvailableBalance(Asset.BRL));
        }

        @Test
        void depositForUnknownAccountId_createsAccountImplicitly() {
            AccountId stranger = new AccountId("stranger");
            assertDoesNotThrow(() -> system.deposit(stranger, Asset.BRL, new BigDecimal("10")));
        }
    }

    // -------------------------------------------------------------------------
    // withdraw
    // -------------------------------------------------------------------------

    @Nested
    class Withdraw {

        @Test
        void shouldRejectNullAccountId() {
            assertThrows(NullPointerException.class,
                () -> system.withdraw(null, Asset.BRL, BigDecimal.TEN));
        }

        @Test
        void shouldRejectNullAsset() {
            assertThrows(NullPointerException.class,
                () -> system.withdraw(BUYER_ID, null, BigDecimal.TEN));
        }

        @Test
        void shouldRejectNullAmount() {
            assertThrows(NullPointerException.class,
                () -> system.withdraw(BUYER_ID, Asset.BRL, null));
        }

        @Test
        void shouldRejectZeroAmount() {
            buyer.deposit(Asset.BRL, new BigDecimal("100"));
            assertThrows(IllegalArgumentException.class,
                () -> system.withdraw(BUYER_ID, Asset.BRL, BigDecimal.ZERO));
        }

        @Test
        void shouldRejectNegativeAmount() {
            buyer.deposit(Asset.BRL, new BigDecimal("100"));
            assertThrows(IllegalArgumentException.class,
                () -> system.withdraw(BUYER_ID, Asset.BRL, new BigDecimal("-1")));
        }

        @Test
        void withdrawDecreasesAvailableBalance() {
            buyer.deposit(Asset.BRL, new BigDecimal("100"));
            system.withdraw(BUYER_ID, Asset.BRL, new BigDecimal("40"));

            assertEquals(new BigDecimal("60"), buyer.getAvailableBalance(Asset.BRL));
        }

        @Test
        void withdrawExactBalance_leavesZero() {
            buyer.deposit(Asset.BRL, new BigDecimal("100"));
            system.withdraw(BUYER_ID, Asset.BRL, new BigDecimal("100"));

            assertEquals(BigDecimal.ZERO, buyer.getAvailableBalance(Asset.BRL));
        }

        @Test
        void withdrawMoreThanAvailable_throws() {
            buyer.deposit(Asset.BRL, new BigDecimal("50"));
            assertThrows(IllegalArgumentException.class,
                () -> system.withdraw(BUYER_ID, Asset.BRL, new BigDecimal("100")));
        }

        @Test
        void withdrawFromEmptyBalance_throws() {
            assertThrows(IllegalArgumentException.class,
                () -> system.withdraw(BUYER_ID, Asset.BRL, new BigDecimal("1")));
        }
    }
}
