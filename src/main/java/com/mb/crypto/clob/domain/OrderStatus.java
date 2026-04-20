package com.mb.crypto.clob.domain;

/**
 * Lifecycle state of an order within the order book.
 */
public enum OrderStatus {
    OPEN,
    CANCELED,
    FILLED,
    PARTIALLY_FILLED
}
