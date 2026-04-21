package com.mb.crypto.clob.domain;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents an order resting in or submitted to the order book.
 *
 * <p>Thread-safety: {@code status}, {@code quantity}, and {@code updatedAt} are written
 * exclusively through VarHandle CAS operations. Reads remain plain volatile reads via
 * the field declarations. All writes must occur within a StampedLock write section held
 * by OrderBookEngine, or via the lock-free CAS paths for status/quantity transitions.
 */
public final class Order {

    private static final VarHandle STATUS;
    private static final VarHandle UPDATED_AT;
    private static final VarHandle QUANTITY;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            STATUS     = lookup.findVarHandle(Order.class, "status",    OrderStatus.class);
            UPDATED_AT = lookup.findVarHandle(Order.class, "updatedAt", Instant.class);
            QUANTITY   = lookup.findVarHandle(Order.class, "quantity",  BigDecimal.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

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

    public long getOrderId() {
        return orderId;
    }
    public OrderSide getSide() {
        return side;
    }
    public BigDecimal getPrice() {
        return price;
    }
    public BigDecimal getQuantity() {
        return quantity;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    public OrderStatus getStatus() {
        return status;
    }
    public OrderType getType() {
        return type;
    }
    public AccountId getAccountId() {
        return accountId;
    }
    public Instrument getInstrument() {
        return instrument;
    }

    public void cancel() {
        if (!updateStatus(OrderStatus.OPEN, OrderStatus.CANCELED)) {
            updateStatus(OrderStatus.PARTIALLY_FILLED, OrderStatus.CANCELED);
        }
    }

    public void applyFill() {
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            if (!updateStatus(OrderStatus.OPEN, OrderStatus.FILLED)) {
                updateStatus(OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED);
            }
        } else {
            updateStatus(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED);
        }
    }

    public void decreaseQuantity(BigDecimal amount) {
        updateQuantity(quantity.subtract(amount));
    }

    boolean updateStatus(OrderStatus expected, OrderStatus newStatus) {
        Objects.requireNonNull(expected, "Expected status cannot be null");
        Objects.requireNonNull(newStatus, "New status cannot be null");
        boolean updated = STATUS.compareAndSet(this, expected, newStatus);
        if (updated) {
            UPDATED_AT.setVolatile(this, Instant.now());
        }
        return updated;
    }

    void updateQuantity(BigDecimal newQuantity) {
        Objects.requireNonNull(newQuantity, "Quantity cannot be null");
        if (newQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }

        boolean updated = QUANTITY.compareAndSet(this, quantity, newQuantity);
        if (updated) {
            UPDATED_AT.setVolatile(this, Instant.now());
        }
    }
}
