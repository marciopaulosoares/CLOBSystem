package com.mb.crypto.loadtest;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates realistic buy/sell orders for load testing.
 * Uses a price model with configurable spread and volatility.
 */
public class OrderGenerator {
    private final LoadProfile loadProfile;
    private final Random random;

    public OrderGenerator(LoadProfile loadProfile) {
        this.loadProfile = loadProfile;
        this.random = ThreadLocalRandom.current();
    }

    /**
     * Generate a random account ID
     */
    public String generateAccountId() {
        int accountId = random.nextInt(loadProfile.getNumberOfAccounts());
        return "ACCOUNT_" + accountId;
    }

    /**
     * Generate a random price based on volatility and spread
     */
    public double generatePrice() {
        double basePrice = loadProfile.getBasePrice();
        double volatility = loadProfile.getPriceVolatility();

        // Add random volatility to base price
        double randomVariation = (random.nextDouble() - 0.5) * volatility;
        return basePrice + randomVariation;
    }

    /**
     * Generate a buy price (bid side)
     * Slightly lower than base to represent buyer interest
     */
    public double generateBidPrice() {
        double price = generatePrice();
        double spread = loadProfile.getPriceSpread();
        return price - (spread / 2.0);
    }

    /**
     * Generate a sell price (ask side)
     * Slightly higher than base to represent seller interest
     */
    public double generateAskPrice() {
        double price = generatePrice();
        double spread = loadProfile.getPriceSpread();
        return price + (spread / 2.0);
    }

    /**
     * Generate a random quantity
     */
    public int generateQuantity() {
        // Random quantity between 1 and 1000 units
        return random.nextInt(1, 1001);
    }

    /**
     * Generate a random order ID (for cancellations)
     */
    public String generateOrderId() {
        return "ORDER_" + System.nanoTime() + "_" + random.nextInt(100000);
    }

    /**
     * Determine operation type: NEW_ORDER, CANCEL, or QUERY
     * Based on LoadProfile percentages
     */
    public OperationType determineOperationType() {
        int rand = random.nextInt(100);
        int newOrderThreshold = loadProfile.getNewOrderPercentage();
        int cancelThreshold = newOrderThreshold + loadProfile.getCancelPercentage();

        if (rand < newOrderThreshold) {
            return OperationType.NEW_ORDER;
        } else if (rand < cancelThreshold) {
            return OperationType.CANCEL;
        } else {
            return OperationType.QUERY;
        }
    }

    /**
     * Determine if order is BUY or SELL
     */
    public OrderSide determineSide() {
        return random.nextBoolean() ? OrderSide.BUY : OrderSide.SELL;
    }

    public enum OperationType {
        NEW_ORDER,
        CANCEL,
        QUERY
    }

    public enum OrderSide {
        BUY,
        SELL
    }

    /**
     * Order DTO for passing data
     */
    public static class OrderData {
        public final String accountId;
        public final double price;
        public final int quantity;
        public final OrderSide side;
        public final OperationType operationType;

        public OrderData(String accountId, double price, int quantity,
                        OrderSide side, OperationType operationType) {
            this.accountId = accountId;
            this.price = price;
            this.quantity = quantity;
            this.side = side;
            this.operationType = operationType;
        }

        @Override
        public String toString() {
            return "OrderData{" +
                    "accountId='" + accountId + '\'' +
                    ", side=" + side +
                    ", price=" + String.format("%.2f", price) +
                    ", quantity=" + quantity +
                    ", operationType=" + operationType +
                    '}';
        }
    }

    /**
     * Generate a complete order
     */
    public OrderData generateOrder() {
        String accountId = generateAccountId();
        OperationType operationType = determineOperationType();
        OrderSide side = determineSide();
        double price = side == OrderSide.BUY ? generateBidPrice() : generateAskPrice();
        int quantity = generateQuantity();

        return new OrderData(accountId, price, quantity, side, operationType);
    }
}
