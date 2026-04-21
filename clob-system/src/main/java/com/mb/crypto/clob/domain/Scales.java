package com.mb.crypto.clob.domain;

/**
 * Fixed-point scale constants for price and quantity representations.
 *
 * <p>Prices are stored as plain integer BRL (no decimal places). Quantities are stored
 * as satoshis (10^8 units per BTC). Keeping PRICE_DECIMALS at 0 means the long value
 * is directly human-readable as whole BRL and avoids scale-mismatch issues when
 * converting back to BigDecimal for settlement. Raise PRICE_DECIMALS to 2 (centavos)
 * if sub-BRL precision is required.
 */
public final class Scales {

    public static final int  PRICE_DECIMALS    = 0;
    public static final int  QUANTITY_DECIMALS = 8;
    public static final long PRICE_SCALE       = 1L;
    public static final long QUANTITY_SCALE    = 100_000_000L;

    private Scales() {}
}
