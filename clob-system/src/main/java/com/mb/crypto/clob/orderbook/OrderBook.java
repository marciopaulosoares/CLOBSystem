package com.mb.crypto.clob.orderbook;

import com.mb.crypto.clob.domain.Instrument;
import com.mb.crypto.clob.domain.Order;
import com.mb.crypto.clob.domain.OrderSide;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Price-level order book for a single instrument.
 *
 * <p>Bids are stored in descending price order (highest bid = best bid = bidPrices().first()).
 * Asks are stored in ascending price order (lowest ask = best ask = askPrices().first()).
 * Price keys are stored as long (integer BRL, Scales.PRICE_DECIMALS = 0).
 *
 * <p>Order nodes live in a direct {@link ByteBuffer} arena. Each 8-byte slot is a node
 * in a per-price-level doubly-linked list:
 * <pre>
 *   [0..3]  prevSlot (int, NO_SLOT = -1)
 *   [4..7]  nextSlot (int, NO_SLOT = -1)
 * </pre>
 * This makes cancelOrder O(1) (pointer surgery) instead of the former O(k) linear
 * scan through an ArrayDeque, where k was the number of orders at the same price.
 * addOrder is O(1) amortized; O(log n) only when a new price level enters the TreeMap.
 *
 * <p>Not thread-safe by itself: all access must be guarded by the StampedLock
 * obtained from InstrumentLockRegistry for the corresponding instrument.
 */
public final class OrderBook {

    private static final int SLOT_SIZE   = 8;          // [prev:4][next:4]
    private static final int OFF_PREV    = 0;
    private static final int OFF_NEXT    = 4;
    private static final int NO_SLOT     = -1;
    private static final int INITIAL_CAP = 1 << 12;   // 4 096 slots = 32 KB

    private final Instrument instrument;

    // price → [headSlot, tailSlot, count]
    private final NavigableMap<Long, int[]> bids;
    private final NavigableMap<Long, int[]> asks;

    private final Map<Long, Order>   ordersById = new HashMap<>();
    private final Map<Long, Integer> slotById   = new HashMap<>();
    private       Order[]            orderBySlot;

    private ByteBuffer arena;
    private int        slotCapacity;
    private int        nextFresh = 0;
    private int        freeHead  = NO_SLOT;

    public OrderBook(Instrument instrument) {
        this.instrument   = Objects.requireNonNull(instrument);
        this.bids         = new TreeMap<>(Comparator.reverseOrder());
        this.asks         = new TreeMap<>();
        this.slotCapacity = INITIAL_CAP;
        this.arena        = ByteBuffer.allocateDirect(slotCapacity * SLOT_SIZE);
        this.orderBySlot  = new Order[slotCapacity];
    }

    public Instrument getInstrument() {
        return instrument;
    }

    /** Live, ordered view of bid prices (descending: highest first). */
    public NavigableSet<Long> bidPrices() {
        return bids.navigableKeySet();
    }

    /** Live, ordered view of ask prices (ascending: lowest first). */
    public NavigableSet<Long> askPrices() {
        return asks.navigableKeySet();
    }

    // -----------------------------------------------------------------------
    // Price-level queue operations — O(1) each
    // -----------------------------------------------------------------------

    /** O(1) — peek at the oldest (first-in) resting order at this price level. */
    public Order peekHead(long price, OrderSide side) {
        int[] level = sideMap(side).get(price);
        return (level != null) ? orderBySlot[level[0]] : null;
    }

    /**
     * O(1) — remove and return the oldest resting order at this price level.
     * Automatically removes the price level from the book when it becomes empty.
     * Also removes the order from ordersById / slotById so that a subsequent
     * cancelOrder correctly rejects already-filled orders.
     */
    public Order pollHead(long price, OrderSide side) {
        NavigableMap<Long, int[]> map = sideMap(side);
        int[] level = map.get(price);
        if (level == null) {
            return null;
        }

        int   headSlot = level[0];
        Order order    = orderBySlot[headSlot];
        int   nextSlot = readInt(headSlot, OFF_NEXT);

        level[0] = nextSlot;
        level[2]--;
        if (nextSlot == NO_SLOT) {
            map.remove(price);
        } else {
            writeInt(nextSlot, OFF_PREV, NO_SLOT);
        }

        freeSlot(headSlot);
        ordersById.remove(order.getOrderId());
        slotById.remove(order.getOrderId());

        return order;
    }

    /** O(1). */
    public boolean isLevelEmpty(long price, OrderSide side) {
        return sideMap(side).get(price) == null;
    }

    /** O(1) — number of resting orders at this price level. */
    public int levelSize(long price, OrderSide side) {
        int[] level = sideMap(side).get(price);
        return (level != null) ? level[2] : 0;
    }

    // -----------------------------------------------------------------------
    // Core book operations
    // -----------------------------------------------------------------------

    /** O(1) amortized — append order to the tail of its price level. */
    public void addOrder(Order order) {
        Objects.requireNonNull(order);

        long price = order.getPriceLong();
        NavigableMap<Long, int[]> map = sideMap(order.getSide());

        int slot = allocSlot();
        writeInt(slot, OFF_PREV, NO_SLOT);
        writeInt(slot, OFF_NEXT, NO_SLOT);
        orderBySlot[slot] = order;

        int[] level = map.get(price);
        if (level == null) {
            map.put(price, new int[]{slot, slot, 1});
        } else {
            int tail = level[1];
            writeInt(tail, OFF_NEXT, slot);
            writeInt(slot, OFF_PREV, tail);
            level[1] = slot;
            level[2]++;
        }

        ordersById.put(order.getOrderId(), order);
        slotById.put(order.getOrderId(), slot);
    }

    /** O(1) — unlink order from its price level via pointer surgery. */
    public void cancelOrder(Order order) {
        Objects.requireNonNull(order);

        long    orderId = order.getOrderId();
        Order   stored  = ordersById.remove(orderId);
        Integer slotObj = slotById.remove(orderId);

        if (stored == null || slotObj == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }

        int slot = slotObj;
        int prev = readInt(slot, OFF_PREV);
        int next = readInt(slot, OFF_NEXT);

        NavigableMap<Long, int[]> map = sideMap(stored.getSide());
        int[] level = map.get(stored.getPriceLong());

        if (prev == NO_SLOT) {
            level[0] = next;
        } else {
            writeInt(prev, OFF_NEXT, next);
        }
        if (next == NO_SLOT) {
            level[1] = prev;
        } else {
            writeInt(next, OFF_PREV, prev);
        }
        level[2]--;

        if (level[2] == 0) {
            map.remove(stored.getPriceLong());
        }

        freeSlot(slot);
        stored.cancel();
    }

    // -----------------------------------------------------------------------
    // Arena allocator — O(1) amortized
    // -----------------------------------------------------------------------

    private int allocSlot() {
        if (freeHead != NO_SLOT) {
            int slot = freeHead;
            freeHead = readInt(slot, OFF_NEXT);
            return slot;
        }
        if (nextFresh == slotCapacity) {
            grow();
        }
        return nextFresh++;
    }

    private void freeSlot(int slot) {
        orderBySlot[slot] = null;
        writeInt(slot, OFF_NEXT, freeHead);
        freeHead = slot;
    }

    private void grow() {
        int        newCap   = slotCapacity << 1;
        ByteBuffer newArena = ByteBuffer.allocateDirect(newCap * SLOT_SIZE);
        arena.clear();
        newArena.put(arena);
        newArena.clear();
        arena        = newArena;
        orderBySlot  = Arrays.copyOf(orderBySlot, newCap);
        slotCapacity = newCap;
    }

    // -----------------------------------------------------------------------
    // ByteBuffer helpers (absolute-position accessors — no shared state)
    // -----------------------------------------------------------------------

    private NavigableMap<Long, int[]> sideMap(OrderSide side) {
        return side == OrderSide.BUY ? bids : asks;
    }

    private int readInt(int slot, int off) {
        return arena.getInt(slot * SLOT_SIZE + off);
    }

    private void writeInt(int slot, int off, int value) {
        arena.putInt(slot * SLOT_SIZE + off, value);
    }
}
