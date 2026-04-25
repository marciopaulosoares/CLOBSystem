package com.mb.crypto.clob.matching;

import com.mb.crypto.clob.domain.Account;
import com.mb.crypto.clob.domain.AccountId;
import com.mb.crypto.clob.domain.Order;
import com.mb.crypto.clob.domain.OrderSide;
import com.mb.crypto.clob.orderbook.OrderBook;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;

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

        OrderSide      restingSide = incoming.getSide() == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;
        NavigableSet<Long> prices  = incoming.getSide() == OrderSide.BUY
                ? orderBook.askPrices()
                : orderBook.bidPrices();

        while (incoming.getQuantityLong() > 0 && !prices.isEmpty()) {

            long bestPrice = prices.first();

            boolean priceMatches = incoming.getSide() == OrderSide.BUY
                    ? bestPrice <= incoming.getPriceLong()
                    : bestPrice >= incoming.getPriceLong();

            if (!priceMatches) {
                break;
            }

            while (!orderBook.isLevelEmpty(bestPrice, restingSide) && incoming.getQuantityLong() > 0) {

                Order resting  = orderBook.peekHead(bestPrice, restingSide);
                long tradedQty = Math.min(incoming.getQuantityLong(), resting.getQuantityLong());

                matches.add(new MatchedPair(incoming, resting, bestPrice, tradedQty));

                incoming.decreaseQuantity(tradedQty);
                resting.decreaseQuantity(tradedQty);

                if (resting.getQuantityLong() == 0) {
                    orderBook.pollHead(bestPrice, restingSide);
                }
            }
        }

        return matches;
    }
}
