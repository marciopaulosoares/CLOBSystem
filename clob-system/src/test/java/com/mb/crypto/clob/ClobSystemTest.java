package com.mb.crypto.clob;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClobSystemTest {

    private static final Instrument BTC_BRL = new Instrument(Asset.BTC, Asset.BRL);
    private static final AccountId BUYER_ID  = new AccountId("buyer");
    private static final AccountId SELLER_ID = new AccountId("seller");

    private Account buyer;
    private Account seller;
    private ClobSystem clob;

    @BeforeEach
    void setUp() {
        buyer  = new Account(BUYER_ID);
        seller = new Account(SELLER_ID);
        buyer.deposit(Asset.BRL, new BigDecimal("100000"));
        seller.deposit(Asset.BTC, new BigDecimal("10"));
        clob = new ClobSystem(List.of(BTC_BRL), List.of(buyer, seller));
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
    class Constructor {

        @Test
        void nullInstruments_throwsNullPointerException() {
            assertThrows(NullPointerException.class,
                () -> new ClobSystem(null, List.of()));
        }

        @Test
        void nullAccounts_throwsNullPointerException() {
            assertThrows(NullPointerException.class,
                () -> new ClobSystem(List.of(BTC_BRL), null));
        }

        @Test
        void duplicateAccountIds_throwsIllegalStateException() {
            Account duplicate = new Account(BUYER_ID);
            assertThrows(IllegalStateException.class,
                () -> new ClobSystem(List.of(BTC_BRL), List.of(buyer, duplicate)));
        }
    }

    @Nested
    class PlaceOrder {

        @Test
        void noMatch_orderRestsInBook() {
            clob.placeOrder(buyOrder(1, "500", "1"));

            OrderBook book = clob.getOrderBook(BTC_BRL);
            assertEquals(1, book.getBids().get(500L).size());
        }

        @Test
        void fullMatch_executesTradeAndClearsBook() {
            clob.placeOrder(sellOrder(1, "500", "1"));
            clob.placeOrder(buyOrder(2, "500", "1"));

            OrderBook book = clob.getOrderBook(BTC_BRL);
            assertTrue(book.getBids().isEmpty());
            assertTrue(book.getAsks().isEmpty());
        }

        @Test
        void fullMatch_settlesBothAccounts() {
            clob.placeOrder(sellOrder(1, "500", "1"));
            clob.placeOrder(buyOrder(2, "500", "1"));

            assertBd("1", buyer.getAvailableBalance(Asset.BTC));
            assertBd("500", seller.getAvailableBalance(Asset.BRL));
        }

        @Test
        void nullOrder_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> clob.placeOrder(null));
        }
    }

    @Nested
    class CancelOrder {

        @Test
        void cancelOpenOrder_removesFromBook() {
            Order buy = buyOrder(1, "500", "1");
            clob.placeOrder(buy);
            clob.cancelOrder(buy);

            assertTrue(clob.getOrderBook(BTC_BRL).getBids().isEmpty());
            assertEquals(OrderStatus.CANCELED, buy.getStatus());
        }

        @Test
        void cancelOrder_releasesLockedFunds() {
            Order buy = buyOrder(1, "500", "1");
            clob.placeOrder(buy);
            clob.cancelOrder(buy);

            assertBd("0", buyer.getLockedBalance(Asset.BRL));
            assertBd("100000", buyer.getAvailableBalance(Asset.BRL));
        }

        @Test
        void cancelFilledOrder_throwsIllegalStateException() {
            Order sell = sellOrder(1, "500", "1");
            clob.placeOrder(sell);
            clob.placeOrder(buyOrder(2, "500", "1"));

            assertThrows(IllegalStateException.class, () -> clob.cancelOrder(sell));
        }

        @Test
        void nullOrder_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> clob.cancelOrder(null));
        }
    }

    @Nested
    class Deposit {

        @Test
        void depositsToExistingAccount() {
            clob.deposit(BUYER_ID, Asset.BRL, new BigDecimal("500"));

            assertBd("100500", buyer.getAvailableBalance(Asset.BRL));
        }

        @Test
        void depositsCreatesAccountOnTheFly() {
            AccountId newId = new AccountId("new");
            clob.deposit(newId, Asset.BTC, new BigDecimal("5"));

            // verify via subsequent placeOrder — account must hold the deposited balance
            Order sell = new Order(99, OrderSide.SELL, new BigDecimal("500"),
                new BigDecimal("1"), OrderType.LIMIT, newId, BTC_BRL);
            clob.placeOrder(sell);

            assertEquals(1, clob.getOrderBook(BTC_BRL).getAsks().get(500L).size());
        }

        @Test
        void nullAccountId_throwsNullPointerException() {
            assertThrows(NullPointerException.class,
                () -> clob.deposit(null, Asset.BRL, BigDecimal.ONE));
        }

        @Test
        void nullAsset_throwsNullPointerException() {
            assertThrows(NullPointerException.class,
                () -> clob.deposit(BUYER_ID, null, BigDecimal.ONE));
        }

        @Test
        void nullAmount_throwsNullPointerException() {
            assertThrows(NullPointerException.class,
                () -> clob.deposit(BUYER_ID, Asset.BRL, null));
        }
    }

    @Nested
    class Withdraw {

        @Test
        void withdrawReducesAvailableBalance() {
            clob.withdraw(BUYER_ID, Asset.BRL, new BigDecimal("1000"));

            assertBd("99000", buyer.getAvailableBalance(Asset.BRL));
        }

        @Test
        void withdrawMoreThanAvailable_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                () -> clob.withdraw(BUYER_ID, Asset.BRL, new BigDecimal("200000")));
        }

        @Test
        void withdrawLockedFunds_throwsIllegalArgumentException() {
            clob.placeOrder(buyOrder(1, "500", "1")); // locks 500 BRL

            // only 99500 available — trying to withdraw 100000 must fail
            assertThrows(IllegalArgumentException.class,
                () -> clob.withdraw(BUYER_ID, Asset.BRL, new BigDecimal("100000")));
        }

        @Test
        void nullAccountId_throwsNullPointerException() {
            assertThrows(NullPointerException.class,
                () -> clob.withdraw(null, Asset.BRL, BigDecimal.ONE));
        }

        @Test
        void nullAsset_throwsNullPointerException() {
            assertThrows(NullPointerException.class,
                () -> clob.withdraw(BUYER_ID, null, BigDecimal.ONE));
        }

        @Test
        void nullAmount_throwsNullPointerException() {
            assertThrows(NullPointerException.class,
                () -> clob.withdraw(BUYER_ID, Asset.BRL, null));
        }
    }

    @Nested
    class AddAccount {

        @Test
        void addsNewAccount() {
            AccountId newId = new AccountId("new");
            Account newAccount = new Account(newId);
            newAccount.deposit(Asset.BTC, new BigDecimal("3"));
            clob.addAccount(newAccount);

            Order sell = new Order(99, OrderSide.SELL, new BigDecimal("500"),
                new BigDecimal("1"), OrderType.LIMIT, newId, BTC_BRL);
            clob.placeOrder(sell);

            assertEquals(1, clob.getOrderBook(BTC_BRL).getAsks().get(500L).size());
        }

        @Test
        void addExistingAccountId_doesNotOverwrite() {
            Account impostor = new Account(BUYER_ID);
            impostor.deposit(Asset.BRL, new BigDecimal("1"));
            clob.addAccount(impostor);

            // original buyer balance unchanged (putIfAbsent semantics)
            assertBd("100000", buyer.getAvailableBalance(Asset.BRL));
        }

        @Test
        void nullAccount_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> clob.addAccount(null));
        }
    }

    @Nested
    class GetOrderBook {

        @Test
        void returnsBookForRegisteredInstrument() {
            OrderBook book = clob.getOrderBook(BTC_BRL);

            assertNotNull(book);
            assertEquals(BTC_BRL, book.getInstrument());
        }

        @Test
        void nullInstrument_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> clob.getOrderBook(null));
        }
    }
}
