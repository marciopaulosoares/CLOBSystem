package com.mb.crypto.clob.domain;

/**
 * Indicates whether an order is a buy or sell.
 */
public enum OrderSide {
    BUY(0),
    SELL(1);

    private final byte code;

    OrderSide(int code) {
        this.code = (byte) code;
    }

    public byte code() {
        return code;
    }

    public static OrderSide from(byte code) {
        return code == 0 ? BUY : SELL;
    }
}
