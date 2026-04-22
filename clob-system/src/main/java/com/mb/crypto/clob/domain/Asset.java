package com.mb.crypto.clob.domain;

/**
 * Represents a tradeable asset in the CLOB system.
 * BTC is the base asset; BRL is the quote (pricing) asset.
 */
public enum Asset {
    BTC(0),
    BRL(1);

    private final byte code;

    Asset(int code) {
        this.code = (byte) code;
    }

    public byte code() {
        return code;
    }

    public static Asset from(byte code) {
        return code == 0 ? BTC : BRL;
    }
}
