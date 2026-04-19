package com.mb.crypto.clob.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents an order resting in or submitted to the order book.
 *
 * <p>Thread-safety: {@code status} and {@code quantity} are {@code volatile} to allow
 * safe lock-free reads. All writes must occur within a StampedLock write section
 * held by OrderBookEngine.
 *
 * <p>TODO: Consider VarHandle for compareAndSet updates to status and quantity
 * to enable fully lock-free partial-fill and cancellation paths.
 */
public final class Order {

    private final long orderId;
    private final OrderSide side;
    private final BigDecimal price;
    private volatile BigDecimal quantity;
    private final Instant createdAt;
    private volatile Instant updatedAt;
    private volatile OrderStatus status;
    private final OrderType type;
    private final AccountId accountId;
    private final Instrument instrument;

    /**
     * Creates a new OPEN order. Price and quantity must both be positive.
     */
    public Order(
        long orderId,
        OrderSide side,
        BigDecimal price,
        BigDecimal quantity,
        OrderType type,
        AccountId accountId,
        Instrument instrument) {
        this.orderId = orderId;
        this.side = Objects.requireNonNull(side, "Side cannot be null");
        this.price = Objects.requireNonNull(price, "Price cannot be null");
        this.quantity = Objects.requireNonNull(quantity, "Quantity cannot be null");
        this.type = Objects.requireNonNull(type, "Type cannot be null");
        this.accountId = Objects.requireNonNull(accountId, "AccountId cannot be null");
        this.instrument = Objects.requireNonNull(instrument, "Instrument cannot be null");
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.status = OrderStatus.OPEN;
    }

    /**
     * Returns the unique order identifier.
     */
    public long getOrderId() {
        return orderId;
    }

    /**
     * Returns the order side (BUY or SELL).
     */
    public OrderSide getSide() {
        return side;
    }

    /**
     * Returns the limit price.
     */
    public BigDecimal getPrice() {
        return price;
    }

    /**
     * Returns the current remaining quantity.
     */
    public BigDecimal getQuantity() {
        return quantity;
    }

    /**
     * Returns the timestamp when this order was created.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the timestamp of the most recent update to this order.
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Returns the current order status.
     */
    public OrderStatus getStatus() {
        return status;
    }

    /**
     * Returns the order type (LIMIT or MARKET).
     */
    public OrderType getType() {
        return type;
    }

    /**
     * Returns the identifier of the account that submitted this order.
     */
    public AccountId getAccountId() {
        return accountId;
    }

    /**
     * Returns the instrument this order is for.
     */
    public Instrument getInstrument() {
        return instrument;
    }

    void updateQuantity(BigDecimal newQuantity) {
        Objects.requireNonNull(newQuantity, "Quantity cannot be null");
        if (newQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        // TODO: Consider VarHandle.setVolatile for lock-free update
        this.quantity = newQuantity;
        this.updatedAt = Instant.now();
    }

    void updateStatus(OrderStatus newStatus) {
        Objects.requireNonNull(newStatus, "Status cannot be null");
        // TODO: Consider VarHandle.setVolatile for lock-free update
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }
}
