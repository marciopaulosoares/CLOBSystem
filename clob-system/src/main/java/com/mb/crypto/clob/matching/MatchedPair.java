package com.mb.crypto.clob.matching;

import com.mb.crypto.clob.domain.Order;

import java.util.Objects;

/**
 * Represents the outcome of a single fill between an incoming and a resting order.
 * Carries only the matched data — trade identity and settlement are handled by
 * OrderBookEngine, which owns the tradeId sequence and account mutations.
 *
 * <p>price is stored as integer BRL (Scales.PRICE_DECIMALS = 0).
 * qty is stored as satoshis (Scales.QUANTITY_DECIMALS = 8).
 */
record MatchedPair(Order incoming, Order resting, long price, long qty) {

    MatchedPair {
        Objects.requireNonNull(incoming, "Incoming order cannot be null");
        Objects.requireNonNull(resting, "Resting order cannot be null");
        if (price <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("Qty must be positive");
        }
    }
}
