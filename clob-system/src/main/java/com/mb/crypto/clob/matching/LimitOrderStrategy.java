package com.mb.crypto.clob.matching;

import com.mb.crypto.clob.domain.Account;
import com.mb.crypto.clob.domain.AccountId;
import com.mb.crypto.clob.domain.Order;
import com.mb.crypto.clob.domain.OrderSide;
import com.mb.crypto.clob.orderbook.OrderBook;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Price-time priority matching strategy for LIMIT orders.
 *
 * <p>A buy limit order matches against resting asks whose price is less than or equal
 * to the order's limit price. A sell limit order matches against resting bids whose
 * price is greater than or equal to the order's limit price. Partial fills are
 * supported; unmatched remainder rests in the book.
 *
 * <p>All price and quantity comparisons use primitive long to avoid BigDecimal allocations
 * on the hot path.
 */
public final class LimitOrderStrategy implements OrderMatcher {

    @Override
    public List<MatchedPair> match(Order incoming, OrderBook orderBook, Map<AccountId, Account> accounts) {

        List<MatchedPair> matches = new ArrayList<>(4);

        NavigableMap<Long, ArrayDeque<Order>> book =
            incoming.getSide() == OrderSide.BUY
                ? orderBook.getAsks()
                : orderBook.getBids();

        while (incoming.getQuantityLong() > 0 && !book.isEmpty()) {

            Map.Entry<Long, ArrayDeque<Order>> bestEntry = book.firstEntry();
            long bestPrice = bestEntry.getKey();

            boolean priceMatches =
                incoming.getSide() == OrderSide.BUY
                    ? bestPrice <= incoming.getPriceLong()
                    : bestPrice >= incoming.getPriceLong();

            if (!priceMatches) {
                break;
            }

            ArrayDeque<Order> queue = bestEntry.getValue();

            while (!queue.isEmpty() && incoming.getQuantityLong() > 0) {

                Order resting = queue.peek();
                long tradedQty = Math.min(incoming.getQuantityLong(), resting.getQuantityLong());

                matches.add(new MatchedPair(incoming, resting, bestPrice, tradedQty));

                incoming.decreaseQuantity(tradedQty);
                resting.decreaseQuantity(tradedQty);

                if (resting.getQuantityLong() == 0) {
                    queue.poll();
                }
            }

            if (queue.isEmpty()) {
                OrderSide restingSide = incoming.getSide() == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;
                orderBook.purgeEmptyLevel(bestPrice, restingSide);
            }
        }

        return matches;
    }
}
