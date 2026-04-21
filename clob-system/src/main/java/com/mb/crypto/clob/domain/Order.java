package com.mb.crypto.clob.domain;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

// ---------------------------------------------------------------------------
// Cache-line padding hierarchy
//
// HotSpot places superclass fields before subclass fields and groups fields
// by size within each class (oops first, then longs). We exploit both rules
// to push the volatile fields onto cache lines distinct from the immutable
// fields in Order, preventing read/write false sharing across CPUs.
//
// Verified layout (HotSpot 21, compressed-oops, 12-byte header):
//
//   offset  12 –  15 : statusOrdinal   (int,  4 b — backfilled into alignment gap)
//   offset  16 –  71 : OrderLeadPad    (7 × long, 56 b)               ← cache line 0
//   offset  72 –  79 : quantityLong    (long, 8 b)                     ← cache line 1
//   offset  80 –  87 : updatedAtMillis (long, 8 b)                     ← cache line 1
//   offset  88 – 143 : OrderTrailPad   (7 × long, 56 b)                ← cache line 1
//   offset 144+      : Order immutable fields (orderId, priceLong, ...) ← cache line 2
//
// statusOrdinal (int) backfills into the 4-byte gap before the first long; the other
// two volatile longs land on cache line 1; all immutables start on cache line 2.
// All three hot fields are now primitive — no object allocation on write.
// ---------------------------------------------------------------------------

/** Leading padding: pushes the volatile fields past the first 64-byte cache-line boundary. */
abstract class OrderLeadPad {
    @SuppressWarnings("unused")
    long p01, p02, p03, p04, p05, p06, p07;
}

/** The hot volatile fields, isolated from the immutable fields by surrounding padding. */
abstract class OrderHotFields extends OrderLeadPad {
    volatile int  statusOrdinal;
    volatile long quantityLong;
    volatile long updatedAtMillis;
}

/**
 * Trailing padding after the volatile fields so that the immutable fields in
 * {@link Order} are pushed onto a fresh cache line and cannot be invalidated by
 * writes to the volatile fields above.
 */
abstract class OrderTrailPad extends OrderHotFields {
    @SuppressWarnings("unused")
    long p11, p12, p13, p14, p15, p16, p17;
}

/**
 * Represents an order resting in or submitted to the order book.
 *
 * <p>Prices are stored as {@code long} integer BRL ({@link Scales#PRICE_DECIMALS} = 0).
 * Quantities are stored as {@code long} satoshis ({@link Scales#QUANTITY_DECIMALS} = 8).
 * This eliminates {@link java.math.BigDecimal} allocations from the matching hot path;
 * conversions back to BigDecimal happen only at the API boundary (lock/settlement).
 *
 * <p>Thread-safety: {@code status}, {@code quantityLong}, and {@code updatedAt} are
 * written exclusively through VarHandle CAS operations. All writes occur within a
 * StampedLock write section held by OrderBookEngine, or via the lock-free CAS paths
 * for status/quantity transitions.
 *
 * <p>Memory layout: the volatile fields are isolated on dedicated cache lines via the
 * {@link OrderLeadPad} / {@link OrderHotFields} / {@link OrderTrailPad} hierarchy to
 * prevent false sharing between concurrent readers and writers.
 */
public final class Order extends OrderTrailPad {

    private static final VarHandle STATUS;
    private static final VarHandle UPDATED_AT;
    private static final VarHandle QUANTITY;

    private static final OrderStatus[] ORDER_STATUS_VALUES = OrderStatus.values();

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            STATUS     = lookup.findVarHandle(OrderHotFields.class, "statusOrdinal",   int.class);
            UPDATED_AT = lookup.findVarHandle(OrderHotFields.class, "updatedAtMillis", long.class);
            QUANTITY   = lookup.findVarHandle(OrderHotFields.class, "quantityLong",    long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final long       orderId;
    private final long       priceLong;
    private final long       createdAtMillis;
    private final OrderSide  side;
    private final OrderType  type;
    private final AccountId  accountId;
    private final Instrument instrument;

    /**
     * Creates a new OPEN order. Price and quantity must both be positive.
     * Price is converted to integer BRL (scale {@link Scales#PRICE_DECIMALS});
     * quantity is converted to satoshis (scale {@link Scales#QUANTITY_DECIMALS}).
     */
    public Order(
        long orderId,
        OrderSide side,
        BigDecimal price,
        BigDecimal quantity,
        OrderType type,
        AccountId accountId,
        Instrument instrument) {
        this.orderId    = orderId;
        this.side       = Objects.requireNonNull(side,       "Side cannot be null");
        this.type       = Objects.requireNonNull(type,       "Type cannot be null");
        this.accountId  = Objects.requireNonNull(accountId,  "AccountId cannot be null");
        this.instrument = Objects.requireNonNull(instrument, "Instrument cannot be null");
        Objects.requireNonNull(price,    "Price cannot be null");
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.priceLong       = price.movePointRight(Scales.PRICE_DECIMALS).longValueExact();
        this.quantityLong    = quantity.movePointRight(Scales.QUANTITY_DECIMALS).longValueExact();
        this.createdAtMillis = System.currentTimeMillis();
        this.updatedAtMillis = this.createdAtMillis;
        this.statusOrdinal   = OrderStatus.OPEN.ordinal();
    }

    public long getOrderId() {
        return orderId;
    }

    public OrderSide getSide() {
        return side;
    }

    /** Returns the limit price as integer BRL (lossless conversion from {@link #getPriceLong()}). */
    public BigDecimal getPrice() {
        return BigDecimal.valueOf(priceLong, Scales.PRICE_DECIMALS);
    }

    /** Returns the limit price as a scaled long (integer BRL when PRICE_DECIMALS = 0). */
    public long getPriceLong() {
        return priceLong;
    }

    /**
     * Returns the remaining quantity as a normalized BigDecimal (trailing zeros stripped).
     * Returns {@link BigDecimal#ZERO} when the order is fully filled.
     */
    public BigDecimal getQuantity() {
        long q = quantityLong;
        if (q == 0L) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(q).movePointLeft(Scales.QUANTITY_DECIMALS).stripTrailingZeros();
    }

    /** Returns the remaining quantity in satoshis. */
    public long getQuantityLong() {
        return quantityLong;
    }

    public Instant getCreatedAt() {
        return Instant.ofEpochMilli(createdAtMillis);
    }

    public Instant getUpdatedAt() {
        return Instant.ofEpochMilli(updatedAtMillis);
    }

    public OrderStatus getStatus() {
        return ORDER_STATUS_VALUES[statusOrdinal];
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
        if (quantityLong == 0L) {
            if (!updateStatus(OrderStatus.OPEN, OrderStatus.FILLED)) {
                updateStatus(OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED);
            }
        } else {
            updateStatus(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED);
        }
    }

    /** Decreases remaining quantity by {@code delta} satoshis. */
    public void decreaseQuantity(long delta) {
        updateQuantityLong(quantityLong - delta);
    }

    /** Decreases remaining quantity by the given BigDecimal amount (converted to satoshis). */
    public void decreaseQuantity(BigDecimal amount) {
        decreaseQuantity(amount.movePointRight(Scales.QUANTITY_DECIMALS).longValueExact());
    }

    boolean updateStatus(OrderStatus expected, OrderStatus newStatus) {
        Objects.requireNonNull(expected,  "Expected status cannot be null");
        Objects.requireNonNull(newStatus, "New status cannot be null");
        boolean updated = STATUS.compareAndSet(this, expected.ordinal(), newStatus.ordinal());
        if (updated) {
            UPDATED_AT.setVolatile(this, System.currentTimeMillis());
        }
        return updated;
    }

    void updateQuantityLong(long newQuantity) {
        if (newQuantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        boolean updated = QUANTITY.compareAndSet(this, quantityLong, newQuantity);
        if (updated) {
            UPDATED_AT.setVolatile(this, System.currentTimeMillis());
        }
    }
}
