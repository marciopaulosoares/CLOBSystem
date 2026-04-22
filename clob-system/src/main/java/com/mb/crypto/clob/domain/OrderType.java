package com.mb.crypto.clob.domain;

/**
 * Execution strategy for an order. LIMIT orders rest in the book at a specified price;
 * MARKET orders execute immediately at the best available price.
 */
public enum OrderType {
    LIMIT(0),
    MARKET(1);

    private final byte code;

    OrderType(int code) {
        this.code = (byte) code;
    }

    public byte code() {
        return code;
    }

    public static OrderType from(byte code) {
        return code == 0 ? LIMIT : MARKET;
    }
}
