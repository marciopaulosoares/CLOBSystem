package com.mb.crypto.clob;

import com.mb.crypto.clob.domain.Account;
import com.mb.crypto.clob.domain.AccountId;
import com.mb.crypto.clob.domain.Asset;
import com.mb.crypto.clob.domain.Instrument;
import com.mb.crypto.clob.domain.Order;
import com.mb.crypto.clob.domain.OrderSide;
import com.mb.crypto.clob.domain.OrderStatus;
import com.mb.crypto.clob.domain.OrderType;
import com.mb.crypto.clob.domain.Scales;
import com.mb.crypto.clob.orderbook.OrderBook;

import java.math.BigDecimal;
import java.util.List;
import java.util.NavigableSet;
import java.util.concurrent.atomic.AtomicLong;

public class Main {

    static final Instrument BTC_BRL = new Instrument(Asset.BTC, Asset.BRL);
    static final AtomicLong idGen   = new AtomicLong(1);

    public static void main(String[] args) {
        fullFill();
        partialFill();
        restingOrdersNoMatch();
        cancelOrder();
        multipleMatchesOneAggressor();
    }

    // -----------------------------------------------------------------------
    // Case 1 — full fill: Alice's ask is fully consumed by Bob's matching bid
    // -----------------------------------------------------------------------
    static void fullFill() {
        System.out.println("=== Case 1: Full Fill ===");

        Account alice = funded("alice", BigDecimal.ZERO,       new BigDecimal("1"));   // 1 BTC
        Account bob   = funded("bob",   new BigDecimal("500"), BigDecimal.ZERO);        // 500 BRL

        ClobSystem clob = new ClobSystem(List.of(BTC_BRL), List.of(alice, bob));

        // Alice posts a SELL 1 BTC @ 500 BRL
        Order ask = order(alice, OrderSide.SELL, 500, 1 * Scales.QUANTITY_SCALE);
        clob.placeOrder(ask);

        // Bob places a BUY 1 BTC @ 500 BRL — should fully match
        Order bid = order(bob, OrderSide.BUY, 500, 1 * Scales.QUANTITY_SCALE);
        clob.placeOrder(bid);

        printStatus("alice ask", ask);
        printStatus("bob bid",   bid);
        printBook(clob.getOrderBook(BTC_BRL));
        System.out.printf("alice BRL: %s | bob BTC: %s%n%n",
                alice.getBalance(Asset.BRL), bob.getBalance(Asset.BTC));
    }

    // -----------------------------------------------------------------------
    // Case 2 — partial fill: aggressor consumes only part of the resting order
    // -----------------------------------------------------------------------
    static void partialFill() {
        System.out.println("=== Case 2: Partial Fill ===");

        Account alice = funded("alice", BigDecimal.ZERO,       new BigDecimal("3"));
        Account bob   = funded("bob",   new BigDecimal("1500"), BigDecimal.ZERO);

        ClobSystem clob = new ClobSystem(List.of(BTC_BRL), List.of(alice, bob));

        // Alice posts SELL 3 BTC @ 500
        Order ask = order(alice, OrderSide.SELL, 500, 3 * Scales.QUANTITY_SCALE);
        clob.placeOrder(ask);

        // Bob buys only 1 BTC @ 500 — ask should be partially filled
        Order bid = order(bob, OrderSide.BUY, 500, 1 * Scales.QUANTITY_SCALE);
        clob.placeOrder(bid);

        printStatus("alice ask (3 BTC)", ask);
        printStatus("bob bid  (1 BTC)", bid);
        System.out.printf("ask remaining qty (satoshis): %d%n", ask.getQuantityLong());
        printBook(clob.getOrderBook(BTC_BRL));
        System.out.println();
    }

    // -----------------------------------------------------------------------
    // Case 3 — resting orders, no match: bid below ask stays in book
    // -----------------------------------------------------------------------
    static void restingOrdersNoMatch() {
        System.out.println("=== Case 3: Resting Orders, No Match ===");

        Account alice = funded("alice", BigDecimal.ZERO,       new BigDecimal("1"));
        Account bob   = funded("bob",   new BigDecimal("400"), BigDecimal.ZERO);

        ClobSystem clob = new ClobSystem(List.of(BTC_BRL), List.of(alice, bob));

        // Alice asks 500, Bob bids 400 — prices don't cross
        clob.placeOrder(order(alice, OrderSide.SELL, 500, 1 * Scales.QUANTITY_SCALE));
        clob.placeOrder(order(bob,   OrderSide.BUY,  400, 1 * Scales.QUANTITY_SCALE));

        printBook(clob.getOrderBook(BTC_BRL));
        System.out.println();
    }

    // -----------------------------------------------------------------------
    // Case 4 — cancel: resting order removed before any match
    // -----------------------------------------------------------------------
    static void cancelOrder() {
        System.out.println("=== Case 4: Cancel Order ===");

        Account alice = funded("alice", BigDecimal.ZERO, new BigDecimal("1"));
        ClobSystem clob = new ClobSystem(List.of(BTC_BRL), List.of(alice));

        Order ask = order(alice, OrderSide.SELL, 500, 1 * Scales.QUANTITY_SCALE);
        clob.placeOrder(ask);
        System.out.printf("before cancel — book asks: %d level(s)%n",
                clob.getOrderBook(BTC_BRL).askPrices().size());

        clob.cancelOrder(ask);
        printStatus("alice ask", ask);
        System.out.printf("after cancel  — book asks: %d level(s)%n%n",
                clob.getOrderBook(BTC_BRL).askPrices().size());
    }

    // -----------------------------------------------------------------------
    // Case 5 — one aggressor matches multiple resting levels
    // -----------------------------------------------------------------------
    static void multipleMatchesOneAggressor() {
        System.out.println("=== Case 5: Aggressor Sweeps Multiple Levels ===");

        Account alice = funded("alice", BigDecimal.ZERO,        new BigDecimal("2"));
        Account carol = funded("carol", BigDecimal.ZERO,        new BigDecimal("2"));
        Account bob   = funded("bob",   new BigDecimal("2000"), BigDecimal.ZERO);

        ClobSystem clob = new ClobSystem(List.of(BTC_BRL), List.of(alice, carol, bob));

        // Two resting asks at different price levels
        Order askAlice = order(alice, OrderSide.SELL, 490, 1 * Scales.QUANTITY_SCALE); // cheaper
        Order askCarol = order(carol, OrderSide.SELL, 500, 1 * Scales.QUANTITY_SCALE);
        clob.placeOrder(askAlice);
        clob.placeOrder(askCarol);

        // Bob buys 2 BTC at 500 — sweeps both levels
        Order bid = order(bob, OrderSide.BUY, 500, 2 * Scales.QUANTITY_SCALE);
        clob.placeOrder(bid);

        printStatus("alice ask @490", askAlice);
        printStatus("carol ask @500", askCarol);
        printStatus("bob   bid @500", bid);
        printBook(clob.getOrderBook(BTC_BRL));
        System.out.printf("bob BTC: %s%n%n", bob.getBalance(Asset.BTC));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    static Account funded(String name, BigDecimal brl, BigDecimal btc) {
        Account a = new Account(new AccountId(name));
        if (brl.compareTo(BigDecimal.ZERO) > 0) {
            a.deposit(Asset.BRL, brl);
        }
        if (btc.compareTo(BigDecimal.ZERO) > 0) {
            a.deposit(Asset.BTC, btc);
        }
        return a;
    }

    static Order order(Account account, OrderSide side, long priceBrl, long qtySatoshis) {
        return new Order(idGen.getAndIncrement(), side, priceBrl, qtySatoshis,
                OrderType.LIMIT, account.getId(), BTC_BRL);
    }

    static void printStatus(String label, Order o) {
        OrderStatus s = o.getStatus();
        System.out.printf("  %-20s → %s%n", label, s);
    }

    static void printBook(OrderBook book) {
        System.out.println("  order book:");
        NavigableSet<Long> asks = book.askPrices();
        NavigableSet<Long> bids = book.bidPrices();
        if (asks.isEmpty() && bids.isEmpty()) {
            System.out.println("    (empty)");
            return;
        }
        asks.forEach(price ->
                System.out.printf("    ASK %5d BRL  qty=%d orders%n", price,
                        book.levelSize(price, OrderSide.SELL)));
        bids.forEach(price ->
                System.out.printf("    BID %5d BRL  qty=%d orders%n", price,
                        book.levelSize(price, OrderSide.BUY)));
    }
}
