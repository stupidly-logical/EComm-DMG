package com.ecomm.oms.payment;

import com.ecomm.oms.common.error.PaymentDeclinedException;
import com.ecomm.oms.order.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Charges an order through the {@link PaymentGateway} and records the {@link Payment}. A
 * decline throws {@link PaymentDeclinedException}, rolling back the surrounding checkout
 * transaction so no order or stock change persists.
 */
@Service
public class PaymentService {

    private static final String DEFAULT_METHOD = "MOCK_CARD";

    private final PaymentGateway paymentGateway;
    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentGateway paymentGateway, PaymentRepository paymentRepository) {
        this.paymentGateway = paymentGateway;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Payment charge(Order order, String idempotencyKey, String token, String method) {
        String resolvedMethod = (method == null || method.isBlank()) ? DEFAULT_METHOD : method;
        PaymentGateway.PaymentResult result = paymentGateway.charge(
                new PaymentGateway.ChargeRequest(idempotencyKey, order.getGrandTotal(), token, resolvedMethod));

        if (!result.approved()) {
            throw new PaymentDeclinedException("Payment declined: " + result.declineReason());
        }
        return paymentRepository.save(new Payment(
                order, order.getGrandTotal(), PaymentStatus.APPROVED,
                resolvedMethod, result.gatewayRef(), idempotencyKey));
    }
}
