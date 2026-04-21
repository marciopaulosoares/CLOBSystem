package com.mb.crypto.clob.matching;

import com.mb.crypto.clob.domain.Account;
import com.mb.crypto.clob.domain.AccountId;
import com.mb.crypto.clob.domain.Order;
import com.mb.crypto.clob.domain.OrderSide;
import com.mb.crypto.clob.orderbook.OrderBook;

import java.math.BigDecimal;
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
 */
public final class LimitOrderStrategy implements OrderMatcher {

    @Override
    public List<MatchedPair> match(Order incoming, OrderBook orderBook, Map<AccountId, Account> accounts) {

        List<MatchedPair> matches = new ArrayList<>();

        NavigableMap<BigDecimal, ArrayDeque<Order>> book =
            incoming.getSide() == OrderSide.BUY
                ? orderBook.getAsks()
                : orderBook.getBids();

        while (incoming.getQuantity().compareTo(BigDecimal.ZERO) > 0 && !book.isEmpty()) {

            Map.Entry<BigDecimal, ArrayDeque<Order>> bestEntry = book.firstEntry();

            BigDecimal bestPrice = bestEntry.getKey();

            boolean priceMatches =
                incoming.getSide() == OrderSide.BUY
                    ? bestPrice.compareTo(incoming.getPrice()) <= 0
                    : bestPrice.compareTo(incoming.getPrice()) >= 0;

            if (!priceMatches) {
                break;
            }

            ArrayDeque<Order> queue = bestEntry.getValue();

            while (!queue.isEmpty() && incoming.getQuantity().compareTo(BigDecimal.ZERO) > 0) {

                Order resting = queue.peek();
                BigDecimal tradedQty = incoming.getQuantity().min(resting.getQuantity());

                matches.add(new MatchedPair(incoming, resting, bestPrice, tradedQty));

                incoming.decreaseQuantity(tradedQty);
                resting.decreaseQuantity(tradedQty);

                if (resting.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
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
