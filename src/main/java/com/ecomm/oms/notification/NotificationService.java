package com.ecomm.oms.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mock customer notifications. "Sending" is simulated by logging and persisting the message
 * with status {@code SENT}, which is enough to observe the async pipeline's effects.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final String SENT = "SENT";

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void notifyOrderPlaced(Long orderId, Long customerId) {
        send(customerId, "ORDER_PLACED", "Your order #" + orderId + " has been placed.");
    }

    @Transactional
    public void notifyOrderStatus(Long orderId, Long customerId, String status) {
        send(customerId, "ORDER_" + status, "Order #" + orderId + " is now " + status + ".");
    }

    private void send(Long customerId, String type, String message) {
        log.info("Notifying customer {}: {}", customerId, message);
        notificationRepository.save(new Notification(customerId, type, "EMAIL", message, SENT));
    }
}
