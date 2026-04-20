package com.mb.crypto.clob.matching;

import com.mb.crypto.clob.domain.Order;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Represents the outcome of a single fill between an incoming and a resting order.
 * Carries only the matched data — trade identity and settlement are handled by
 * OrderBookEngine, which owns the tradeId sequence and account mutations.
 */
record MatchedPair(Order incoming, Order resting, BigDecimal price, BigDecimal qty) {

    MatchedPair {
        Objects.requireNonNull(incoming, "Incoming order cannot be null");
        Objects.requireNonNull(resting, "Resting order cannot be null");
        Objects.requireNonNull(price, "Price cannot be null");
        Objects.requireNonNull(qty, "Qty cannot be null");
    }
}
