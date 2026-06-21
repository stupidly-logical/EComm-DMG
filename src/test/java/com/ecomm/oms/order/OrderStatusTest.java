package com.ecomm.oms.order;

import com.ecomm.oms.common.error.ConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Table-driven legality tests for the order state machine (state-machine-transition skill).
 */
class OrderStatusTest {

    private Order newOrder() {
        return new Order(1L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, null);
    }

    @Test
    void legalForwardPathSucceeds() {
        Order order = newOrder();
        order.transitionTo(OrderStatus.CONFIRMED);
        order.transitionTo(OrderStatus.PACKED);
        order.transitionTo(OrderStatus.SHIPPED);
        order.transitionTo(OrderStatus.DELIVERED);
        order.transitionTo(OrderStatus.RETURNED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURNED);
    }

    @Test
    void placedCanBeCancelled() {
        Order order = newOrder();
        order.transitionTo(OrderStatus.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void illegalSkipAheadIsRejected() {
        Order order = newOrder();
        assertThatThrownBy(() -> order.transitionTo(OrderStatus.SHIPPED))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot move order");
    }

    @Test
    void terminalStatesAcceptNoTransitions() {
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.RETURNED)).isTrue();
        assertThat(OrderStatus.RETURNED.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.CONFIRMED)).isFalse();
    }

    @Test
    void cannotCancelOnceShipped() {
        Order order = newOrder();
        order.transitionTo(OrderStatus.CONFIRMED);
        order.transitionTo(OrderStatus.PACKED);
        order.transitionTo(OrderStatus.SHIPPED);
        assertThatThrownBy(() -> order.transitionTo(OrderStatus.CANCELLED))
                .isInstanceOf(ConflictException.class);
    }
}
