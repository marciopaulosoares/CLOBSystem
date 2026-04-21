package com.mb.crypto.clob;

import com.mb.crypto.clob.domain.Account;
import com.mb.crypto.clob.domain.AccountId;
import com.mb.crypto.clob.domain.Asset;
import com.mb.crypto.clob.domain.Instrument;
import com.mb.crypto.clob.domain.Order;
import com.mb.crypto.clob.matching.MatchingEngine;
import com.mb.crypto.clob.matching.OrderBookEngine;
import com.mb.crypto.clob.orderbook.OrderBook;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Top-level facade for the Central Limit Order Book system.
 *
 * <p>Composes the matching engine, account registry, and instrument configuration
 * into a single entry point. Clients interact exclusively through this class;
 * no direct access to OrderBookEngine or OrderBook is required at the application layer.
 */
public final class ClobSystem {

    private final MatchingEngine matchingEngine;
    private final Map<AccountId, Account> accounts;

    public ClobSystem(List<Instrument> instruments, List<Account> accounts) {
        Objects.requireNonNull(instruments, "Instruments cannot be null");
        Objects.requireNonNull(accounts, "Accounts cannot be null");
        this.accounts = accounts.stream()
            .collect(Collectors.toMap(Account::getId, a -> a));
        this.matchingEngine = new OrderBookEngine(instruments, this.accounts);
    }

    public void placeOrder(Order order) {
        Objects.requireNonNull(order, "Order cannot be null");
        matchingEngine.placeOrder(order);
    }

    public void cancelOrder(Order order) {
        Objects.requireNonNull(order, "Order cannot be null");
        matchingEngine.cancelOrder(order);
    }

    public void addAccount(Account account) {
        Objects.requireNonNull(account, "Account cannot be null");
        accounts.putIfAbsent(account.getId(), account);
    }

    public OrderBook getOrderBook(Instrument instrument) {
        Objects.requireNonNull(instrument, "Instrument cannot be null");
        return matchingEngine.getOrderBook(instrument);
    }

    public void deposit(AccountId accountId, Asset asset, BigDecimal amount) {
        Objects.requireNonNull(accountId, "AccountId cannot be null");
        Objects.requireNonNull(asset, "Asset cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");

        accounts.computeIfAbsent(accountId, id -> new Account(id))
            .deposit(asset, amount);
    }

    public void withdraw(AccountId accountId, Asset asset, BigDecimal amount) {
        Objects.requireNonNull(accountId, "AccountId cannot be null");
        Objects.requireNonNull(asset, "Asset cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        accounts.computeIfAbsent(accountId, id -> new Account(id))
            .withdraw(asset, amount);
    }
}
