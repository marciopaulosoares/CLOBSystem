package com.mb.crypto.clob.domain;

/**
 * Lifecycle state of an order within the order book.
 */
public enum OrderStatus {
    OPEN(0),
    CANCELED(1),
    FILLED(2),
    PARTIALLY_FILLED(3);

    private final byte code;

    OrderStatus(int code) {
        this.code = (byte) code;
    }

    public byte code() {
        return code;
    }

    private static final OrderStatus[] VALUES = values();

    public static OrderStatus from(byte code) {
        return VALUES[code];
    }
}
