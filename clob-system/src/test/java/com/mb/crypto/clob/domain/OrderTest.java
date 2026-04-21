package com.mb.crypto.clob.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    private static final long       ORDER_ID   = 1L;
    private static final long       PRICE      = 500L;
    private static final long       QUANTITY   = 100_000_000L;
    private static final OrderSide  SIDE       = OrderSide.BUY;
    private static final OrderType  TYPE       = OrderType.LIMIT;
    private static final AccountId  ACCOUNT_ID = new AccountId("buyer");
    private static final Instrument INSTRUMENT = new Instrument(Asset.BTC, Asset.BRL);

    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order(ORDER_ID, SIDE, PRICE, QUANTITY, TYPE, ACCOUNT_ID, INSTRUMENT);
    }

    @Nested
    class Constructor {

        @Test
        void shouldExposeOrderId() {
            assertEquals(ORDER_ID, order.getOrderId());
        }

        @Test
        void shouldExposeSide() {
            assertEquals(SIDE, order.getSide());
        }

        @Test
        void shouldExposePriceLong() {
            assertEquals(PRICE, order.getPriceLong());
        }

        @Test
        void shouldExposeQuantityLong() {
            assertEquals(QUANTITY, order.getQuantityLong());
        }

        @Test
        void shouldExposeType() {
            assertEquals(TYPE, order.getType());
        }

        @Test
        void shouldExposeAccountId() {
            assertEquals(ACCOUNT_ID, order.getAccountId());
        }

        @Test
        void shouldExposeInstrument() {
            assertEquals(INSTRUMENT, order.getInstrument());
        }

        @Test
        void shouldStartWithOpenStatus() {
            assertEquals(OrderStatus.OPEN, order.getStatus());
        }

        @Test
        void shouldHaveUpdatedAtNotBeforeCreatedAt() {
            assertFalse(order.getUpdatedAt().isBefore(order.getCreatedAt()));
        }

        @Test
        void shouldRejectZeroPrice() {
            assertThrows(IllegalArgumentException.class,
                () -> new Order(ORDER_ID, SIDE, 0L, QUANTITY, TYPE, ACCOUNT_ID, INSTRUMENT));
        }

        @Test
        void shouldRejectNegativePrice() {
            assertThrows(IllegalArgumentException.class,
                () -> new Order(ORDER_ID, SIDE, -1L, QUANTITY, TYPE, ACCOUNT_ID, INSTRUMENT));
        }

        @Test
        void shouldRejectZeroQuantity() {
            assertThrows(IllegalArgumentException.class,
                () -> new Order(ORDER_ID, SIDE, PRICE, 0L, TYPE, ACCOUNT_ID, INSTRUMENT));
        }

        @Test
        void shouldRejectNegativeQuantity() {
            assertThrows(IllegalArgumentException.class,
                () -> new Order(ORDER_ID, SIDE, PRICE, -1L, TYPE, ACCOUNT_ID, INSTRUMENT));
        }

        @Test
        void shouldRejectNullSide() {
            assertThrows(NullPointerException.class,
                () -> new Order(ORDER_ID, null, PRICE, QUANTITY, TYPE, ACCOUNT_ID, INSTRUMENT));
        }

        @Test
        void shouldRejectNullType() {
            assertThrows(NullPointerException.class,
                () -> new Order(ORDER_ID, SIDE, PRICE, QUANTITY, null, ACCOUNT_ID, INSTRUMENT));
        }

        @Test
        void shouldRejectNullAccountId() {
            assertThrows(NullPointerException.class,
                () -> new Order(ORDER_ID, SIDE, PRICE, QUANTITY, TYPE, null, INSTRUMENT));
        }

        @Test
        void shouldRejectNullInstrument() {
            assertThrows(NullPointerException.class,
                () -> new Order(ORDER_ID, SIDE, PRICE, QUANTITY, TYPE, ACCOUNT_ID, null));
        }
    }

    @Nested
    class Cancel {

        @Test
        void shouldTransitionOpenToCanceled() {
            order.cancel();
            assertEquals(OrderStatus.CANCELED, order.getStatus());
        }

        @Test
        void shouldTransitionPartiallyFilledToCanceled() {
            order.updateStatus(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED);
            order.cancel();
            assertEquals(OrderStatus.CANCELED, order.getStatus());
        }

        @Test
        void shouldNotChangeStatusWhenAlreadyFilled() {
            order.updateQuantityLong(0L);
            order.updateStatus(OrderStatus.OPEN, OrderStatus.FILLED);
            order.cancel();
            assertEquals(OrderStatus.FILLED, order.getStatus());
        }

        @Test
        void shouldUpdateUpdatedAt() {
            var before = order.getUpdatedAt();
            order.cancel();
            assertFalse(order.getUpdatedAt().isBefore(before));
        }
    }

    @Nested
    class ApplyFill {

        @Test
        void shouldTransitionOpenToPartiallyFilledWhenQuantityRemains() {
            order.applyFill();
            assertEquals(OrderStatus.PARTIALLY_FILLED, order.getStatus());
        }

        @Test
        void shouldTransitionOpenToFilledWhenQuantityIsZero() {
            order.updateQuantityLong(0L);
            order.applyFill();
            assertEquals(OrderStatus.FILLED, order.getStatus());
        }

        @Test
        void shouldTransitionPartiallyFilledToFilledWhenQuantityIsZero() {
            order.updateStatus(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED);
            order.updateQuantityLong(0L);
            order.applyFill();
            assertEquals(OrderStatus.FILLED, order.getStatus());
        }
    }

    @Nested
    class DecreaseQuantity {

        @Test
        void shouldReduceRemainingQuantity() {
            long delta = 50_000_000L;
            order.decreaseQuantity(delta);
            assertEquals(QUANTITY - delta, order.getQuantityLong());
        }

        @Test
        void shouldAllowDecreaseToZero() {
            order.decreaseQuantity(QUANTITY);
            assertEquals(0L, order.getQuantityLong());
        }

        @Test
        void shouldRejectDeltaExceedingRemainingQuantity() {
            assertThrows(IllegalArgumentException.class,
                () -> order.decreaseQuantity(QUANTITY + 1));
        }
    }

    @Nested
    class UpdateQuantityLong {

        @Test
        void shouldUpdateRemainingQuantity() {
            long newQty = 50_000_000L;
            order.updateQuantityLong(newQty);
            assertEquals(newQty, order.getQuantityLong());
        }

        @Test
        void shouldAllowSettingToZero() {
            order.updateQuantityLong(0L);
            assertEquals(0L, order.getQuantityLong());
        }

        @Test
        void shouldRejectNegativeQuantity() {
            assertThrows(IllegalArgumentException.class,
                () -> order.updateQuantityLong(-1L));
        }
    }
}
