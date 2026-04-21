package com.mb.crypto.clob.domain;

/**
 * Execution strategy for an order. LIMIT orders rest in the book at a specified price;
 * MARKET orders execute immediately at the best available price.
 */
public enum OrderType {
    LIMIT,
    MARKET
}
