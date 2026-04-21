package com.mb.crypto.loadtest;

import com.mb.crypto.clob.domain.OrderSide;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates realistic buy/sell orders for load testing.
 * Uses a price model with configurable spread and volatility.
 */
public class OrderGenerator {
    private final LoadProfile loadProfile;

    public OrderGenerator(LoadProfile loadProfile) {
        this.loadProfile = loadProfile;
    }

    public String generateAccountId() {
        int accountId = ThreadLocalRandom.current().nextInt(loadProfile.getNumberOfAccounts());
        return "account_" + accountId;
    }

    public double generatePrice() {
        double basePrice = loadProfile.getBasePrice();
        double volatility = loadProfile.getPriceVolatility();
        double randomVariation = (ThreadLocalRandom.current().nextDouble() - 0.5) * volatility;
        return basePrice + randomVariation;
    }

    public double generateBidPrice() {
        return generatePrice() - (loadProfile.getPriceSpread() / 2.0);
    }

    public double generateAskPrice() {
        return generatePrice() + (loadProfile.getPriceSpread() / 2.0);
    }

    public int generateQuantity() {
        return ThreadLocalRandom.current().nextInt(1, 101);
    }

    public OperationType determineOperationType() {
        int rand = ThreadLocalRandom.current().nextInt(100);
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

    public OrderSide determineSide() {
        return ThreadLocalRandom.current().nextBoolean() ? OrderSide.BUY : OrderSide.SELL;
    }

    public OrderData generateOrder() {
        String accountId = generateAccountId();
        OperationType operationType = determineOperationType();
        OrderSide side = determineSide();
        double price = (side == OrderSide.BUY) ? generateBidPrice() : generateAskPrice();
        int quantity = generateQuantity();
        return new OrderData(accountId, price, quantity, side, operationType);
    }

    public enum OperationType {
        NEW_ORDER,
        CANCEL,
        QUERY
    }

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
            return "OrderData{accountId='" + accountId + "', side=" + side
                    + ", price=" + String.format("%.2f", price)
                    + ", quantity=" + quantity
                    + ", operationType=" + operationType + '}';
        }
    }
}
