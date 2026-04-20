package com.mb.crypto.clob.validators;

import com.mb.crypto.clob.domain.Order;
import com.mb.crypto.clob.domain.OrderStatus;
import java.util.Objects;

public final class OrderValidator {

    public void validate(Order order) {
        Objects.requireNonNull(order, "Order cannot be null");
        Objects.requireNonNull(order.getInstrument(), "Instrument cannot be null");
        if (order.getStatus() != OrderStatus.OPEN
                && order.getStatus() != OrderStatus.PARTIALLY_FILLED) {
            throw new IllegalStateException(
                "Order " + order.getOrderId() + " is not active: " + order.getStatus());
        }
    }
}
