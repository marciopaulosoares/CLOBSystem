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

    /**
     * Creates the system from the given list of tradeable instruments and participant accounts.
     */
    public ClobSystem(List<Instrument> instruments, List<Account> accounts) {
        Objects.requireNonNull(instruments, "Instruments cannot be null");
        Objects.requireNonNull(accounts, "Accounts cannot be null");
        this.accounts = accounts.stream()
            .collect(Collectors.toMap(Account::getId, a -> a));
        this.matchingEngine = new OrderBookEngine(instruments, this.accounts);
    }

    /**
     * Submits an order to the matching engine for execution.
     */
    public void placeOrder(Order order) {
        Objects.requireNonNull(order, "Order cannot be null");
        matchingEngine.placeOrder(order);
    }

    /**
     * Cancels an existing order identified by its id and instrument.
     */
    public void cancelOrder(Order order) {
        Objects.requireNonNull(order, "Order cannot be null");
        matchingEngine.cancelOrder(order.getOrderId(), order.getInstrument());
    }

    /**
     * Registers a new participant account with the system.
     */
    public void addAccount(Account account) {
        Objects.requireNonNull(account, "Account cannot be null");
        // TODO: implement - accounts.put(account.getId(), account)
    }

    /**
     * Returns the current order book snapshot for the given instrument.
     */
    public OrderBook getOrderBook(Instrument instrument) {
        Objects.requireNonNull(instrument, "Instrument cannot be null");
        return matchingEngine.getOrderBook(instrument);
    }

    /**
     * Credits the specified account with the given asset amount (external deposit).
     */
    public void deposit(AccountId accountId, Asset asset, BigDecimal amount) {
        Objects.requireNonNull(accountId, "AccountId cannot be null");
        Objects.requireNonNull(asset, "Asset cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        // TODO: implement - look up account, call account.deposit(asset, amount)
    }

    /**
     * Debits the specified account by the given asset amount (external withdrawal).
     */
    public void withdraw(AccountId accountId, Asset asset, BigDecimal amount) {
        Objects.requireNonNull(accountId, "AccountId cannot be null");
        Objects.requireNonNull(asset, "Asset cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        // TODO: implement - look up account, call account.withdraw(asset, amount)
    }
}
