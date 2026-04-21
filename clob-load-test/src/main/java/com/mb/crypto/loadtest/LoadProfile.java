package com.mb.crypto.loadtest;

import java.util.Objects;

/**
 * Configuration object for load testing the CLOB system.
 * Defines load parameters, market behavior, and operation distribution.
 */
public class LoadProfile {
    // Concurrency settings
    private final int threadCount;
    private final int testDurationSeconds;
    private final int ordersPerSecond;
    private final int numberOfAccounts;

    // Market behavior tuning
    private final double basePrice;
    private final double priceSpread;           // tight spread → more matches
    private final double priceVolatility;       // random price variation
    private final int initialLiquidityDepth;    // orders to preload

    // Operation distribution (percentages must sum to ~100)
    private final int newOrderPercentage;
    private final int cancelPercentage;
    private final int queryPercentage;

    private LoadProfile(Builder builder) {
        this.threadCount = builder.threadCount;
        this.testDurationSeconds = builder.testDurationSeconds;
        this.ordersPerSecond = builder.ordersPerSecond;
        this.numberOfAccounts = builder.numberOfAccounts;
        this.basePrice = builder.basePrice;
        this.priceSpread = builder.priceSpread;
        this.priceVolatility = builder.priceVolatility;
        this.initialLiquidityDepth = builder.initialLiquidityDepth;
        this.newOrderPercentage = builder.newOrderPercentage;
        this.cancelPercentage = builder.cancelPercentage;
        this.queryPercentage = builder.queryPercentage;

        // Validate percentages
        int total = newOrderPercentage + cancelPercentage + queryPercentage;
        if (total != 100) {
            throw new IllegalArgumentException(
                    "Operation percentages must sum to 100, got: " + total);
        }
    }

    // Getters
    public int getThreadCount() {
        return threadCount;
    }

    public int getTestDurationSeconds() {
        return testDurationSeconds;
    }

    public int getOrdersPerSecond() {
        return ordersPerSecond;
    }

    public int getNumberOfAccounts() {
        return numberOfAccounts;
    }

    public double getBasePrice() {
        return basePrice;
    }

    public double getPriceSpread() {
        return priceSpread;
    }

    public double getPriceVolatility() {
        return priceVolatility;
    }

    public int getInitialLiquidityDepth() {
        return initialLiquidityDepth;
    }

    public int getNewOrderPercentage() {
        return newOrderPercentage;
    }

    public int getCancelPercentage() {
        return cancelPercentage;
    }

    public int getQueryPercentage() {
        return queryPercentage;
    }

    @Override
    public String toString() {
        return "LoadProfile{" +
                "threadCount=" + threadCount +
                ", testDurationSeconds=" + testDurationSeconds +
                ", ordersPerSecond=" + ordersPerSecond +
                ", numberOfAccounts=" + numberOfAccounts +
                ", basePrice=" + basePrice +
                ", priceSpread=" + priceSpread +
                ", priceVolatility=" + priceVolatility +
                ", initialLiquidityDepth=" + initialLiquidityDepth +
                ", newOrderPercentage=" + newOrderPercentage +
                ", cancelPercentage=" + cancelPercentage +
                ", queryPercentage=" + queryPercentage +
                '}';
    }

    /**
     * Builder for LoadProfile
     */
    public static class Builder {
        private int threadCount = 10;
        private int testDurationSeconds = 60;
        private int ordersPerSecond = 1000;
        private int numberOfAccounts = 100;
        private double basePrice = 100.0;
        private double priceSpread = 0.5;
        private double priceVolatility = 2.0;
        private int initialLiquidityDepth = 500;
        private int newOrderPercentage = 70;
        private int cancelPercentage = 20;
        private int queryPercentage = 10;

        public Builder threadCount(int count) {
            this.threadCount = count;
            return this;
        }

        public Builder testDurationSeconds(int seconds) {
            this.testDurationSeconds = seconds;
            return this;
        }

        public Builder ordersPerSecond(int count) {
            this.ordersPerSecond = count;
            return this;
        }

        public Builder numberOfAccounts(int count) {
            this.numberOfAccounts = count;
            return this;
        }

        public Builder basePrice(double price) {
            this.basePrice = price;
            return this;
        }

        public Builder priceSpread(double spread) {
            this.priceSpread = spread;
            return this;
        }

        public Builder priceVolatility(double volatility) {
            this.priceVolatility = volatility;
            return this;
        }

        public Builder initialLiquidityDepth(int depth) {
            this.initialLiquidityDepth = depth;
            return this;
        }

        public Builder newOrderPercentage(int percentage) {
            this.newOrderPercentage = percentage;
            return this;
        }

        public Builder cancelPercentage(int percentage) {
            this.cancelPercentage = percentage;
            return this;
        }

        public Builder queryPercentage(int percentage) {
            this.queryPercentage = percentage;
            return this;
        }

        public LoadProfile build() {
            return new LoadProfile(this);
        }
    }
}
