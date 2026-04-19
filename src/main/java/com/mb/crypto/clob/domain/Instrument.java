package com.mb.crypto.clob.domain;

import java.util.Objects;

/**
 * Immutable value object representing a tradeable pair (e.g. BTC/BRL).
 * The {@code instrumentId} is a derived attribute computed from base and quote assets,
 * used as a human-readable and map-safe key throughout the system.
 */
public record Instrument(Asset base, Asset quote) {

    /**
     * Validates that base and quote assets are non-null and distinct.
     */
    public Instrument {
        Objects.requireNonNull(base, "Base asset cannot be null");
        Objects.requireNonNull(quote, "Quote asset cannot be null");
        if (base == quote) {
            throw new IllegalArgumentException("Base and quote assets must differ");
        }
    }

    /**
     * Returns the derived identifier formed by concatenating base and quote asset names.
     */
    public String instrumentId() {
        return base.name() + quote.name();
    }
}
