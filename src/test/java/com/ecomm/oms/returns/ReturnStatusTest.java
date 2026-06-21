package com.ecomm.oms.returns;

import com.ecomm.oms.common.error.ConflictException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Legality tests for the return-request state machine (state-machine-transition skill).
 */
class ReturnStatusTest {

    private ReturnRequest newReturn() {
        return new ReturnRequest(1L, 2L, "Defective");
    }

    @Test
    void requestedCanBeApprovedThenRefunded() {
        ReturnRequest ret = newReturn();
        ret.transitionTo(ReturnStatus.APPROVED);
        ret.transitionTo(ReturnStatus.REFUNDED);
        assertThat(ret.getStatus()).isEqualTo(ReturnStatus.REFUNDED);
    }

    @Test
    void requestedCanBeRejected() {
        ReturnRequest ret = newReturn();
        ret.transitionTo(ReturnStatus.REJECTED);
        assertThat(ret.getStatus()).isEqualTo(ReturnStatus.REJECTED);
        assertThat(ReturnStatus.REJECTED.isTerminal()).isTrue();
    }

    @Test
    void cannotRefundWithoutApproval() {
        assertThatThrownBy(() -> newReturn().transitionTo(ReturnStatus.REFUNDED))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot move return");
    }

    @Test
    void cannotReopenARefundedReturn() {
        ReturnRequest ret = newReturn();
        ret.transitionTo(ReturnStatus.APPROVED);
        ret.transitionTo(ReturnStatus.REFUNDED);
        assertThatThrownBy(() -> ret.transitionTo(ReturnStatus.APPROVED))
                .isInstanceOf(ConflictException.class);
    }
}
