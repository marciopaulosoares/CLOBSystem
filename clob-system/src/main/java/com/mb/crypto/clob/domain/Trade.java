package com.mb.crypto.clob.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable record of a completed match between a buy order and a sell order.
 */
public record Trade(
    long tradeId,
    long buyOrderId,
    long sellOrderId,
    BigDecimal quantity,
    BigDecimal price,
    Instant executedAt,
    AccountId buyerAccountId,
    AccountId sellerAccountId) {

    public Trade {
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        Objects.requireNonNull(price, "Price cannot be null");
        Objects.requireNonNull(executedAt, "ExecutedAt cannot be null");
        Objects.requireNonNull(buyerAccountId, "BuyerAccountId cannot be null");
        Objects.requireNonNull(sellerAccountId, "SellerAccountId cannot be null");
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Trade quantity must be positive");
        }
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Trade price must be positive");
        }
    }
}
