package com.mb.crypto.clob.matching;

import com.mb.crypto.clob.domain.Account;
import com.mb.crypto.clob.domain.AccountId;
import com.mb.crypto.clob.domain.Order;
import com.mb.crypto.clob.orderbook.OrderBook;
import java.util.Map;

/**
 * Strategy interface for order matching algorithms.
 * Each implementation encapsulates matching rules for a specific OrderType.
 * Called within a StampedLock write section — implementations must not acquire additional locks.
 */
public interface OrderMatcher {

    void match(Order order, OrderBook orderBook, Map<AccountId, Account> accounts);
}
