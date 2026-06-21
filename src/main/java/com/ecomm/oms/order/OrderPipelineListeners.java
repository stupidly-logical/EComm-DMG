package com.ecomm.oms.order;

import com.ecomm.oms.audit.AuditService;
import com.ecomm.oms.fulfillment.FulfillmentService;
import com.ecomm.oms.notification.NotificationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/**
 * The non-blocking downstream pipeline. Each reaction to {@link OrderPlacedEvent} is a
 * separate {@code @Async @TransactionalEventListener(AFTER_COMMIT)} so they run off the
 * request thread, only after the checkout transaction commits, and independently — a failure
 * in one (e.g. routing) does not suppress the others (notification, audit).
 */
@Component
public class OrderPipelineListeners {

    private final FulfillmentService fulfillmentService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public OrderPipelineListeners(FulfillmentService fulfillmentService,
                                  NotificationService notificationService,
                                  AuditService auditService) {
        this.fulfillmentService = fulfillmentService;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void routeFulfillment(OrderPlacedEvent event) {
        fulfillmentService.routeNewOrder(event.orderId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendNotification(OrderPlacedEvent event) {
        notificationService.notifyOrderPlaced(event.orderId(), event.customerId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void recordAudit(OrderPlacedEvent event) {
        auditService.record("Order", event.orderId(), "ORDER_PLACED", "SYSTEM",
                "Order placed by customer " + event.customerId());
    }
}
