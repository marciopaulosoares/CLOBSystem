package com.mb.crypto.clob.matching;

import com.mb.crypto.clob.domain.Account;
import com.mb.crypto.clob.domain.AccountId;
import com.mb.crypto.clob.domain.Order;
import com.mb.crypto.clob.orderbook.OrderBook;
import java.util.Map;
import java.util.Objects;

/**
 * Price-time priority matching strategy for LIMIT orders.
 *
 * <p>A buy limit order matches against resting asks whose price is less than or equal
 * to the order's limit price. A sell limit order matches against resting bids whose
 * price is greater than or equal to the order's limit price. Partial fills are
 * supported; unmatched remainder rests in the book.
 */
public final class LimitOrderStrategy implements OrderMatcher {

    @Override
    public void match(Order order, OrderBook orderBook, Map<AccountId, Account> accounts) {
        Objects.requireNonNull(order, "Order cannot be null");
        Objects.requireNonNull(orderBook, "OrderBook cannot be null");
        Objects.requireNonNull(accounts, "Accounts cannot be null");
        // TODO: implement price-time priority matching:
        //   1. Determine opposite side map (order.getSide() == BUY ? asks : bids)
        //   2. Iterate best price levels while a match condition holds and qty > 0
        //   3. For each matching level deque, fill orders FIFO
        //   4. Calculate filled quantity = min(order.getQuantity(), resting.getQuantity())
        //   5. Call orderBook.executeTrade() or delegate to engine via callback
        //   6. Update quantities and statuses; remove fully-filled resting orders
        //   7. Remove empty price levels from the map
    }
}
