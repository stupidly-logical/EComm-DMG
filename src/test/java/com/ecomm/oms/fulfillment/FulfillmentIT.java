package com.ecomm.oms.fulfillment;

import com.ecomm.oms.audit.AuditLogRepository;
import com.ecomm.oms.cart.dto.AddCartItemRequest;
import com.ecomm.oms.catalog.dto.ProductRequest;
import com.ecomm.oms.inventory.dto.StockAdjustmentRequest;
import com.ecomm.oms.inventory.dto.WarehouseRequest;
import com.ecomm.oms.notification.NotificationRepository;
import com.ecomm.oms.order.OrderStatus;
import com.ecomm.oms.order.dto.CheckoutRequest;
import com.ecomm.oms.fulfillment.dto.FulfillmentStatusRequest;
import com.ecomm.oms.fulfillment.dto.TrackingUpdateRequest;
import com.ecomm.oms.support.IntegrationTestSupport;
import com.ecomm.oms.user.Role;
import com.ecomm.oms.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fulfillment lifecycle (warehouse-staff transitions, tracking, shipment visibility) and the
 * non-blocking AFTER_COMMIT pipeline (auto-confirm + notification + audit).
 */
class FulfillmentIT extends IntegrationTestSupport {

    @Autowired
    private ShipmentRepository shipmentRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    private record Placed(long orderId, long customerId, long warehouseId) {
    }

    private Placed placeOrder(String admin, int qty) throws Exception {
        String productBody = mockMvc.perform(post("/api/products").header(AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ProductRequest("FF-" + System.nanoTime(), "Item", null,
                                new BigDecimal("10.00"), "NONE-" + System.nanoTime(), true, null))))
                .andReturn().getResponse().getContentAsString();
        long productId = objectMapper.readTree(productBody).get("id").asLong();

        String whBody = mockMvc.perform(post("/api/warehouses").header(AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WarehouseRequest("FW-" + System.nanoTime(), "W", "US", 1))))
                .andReturn().getResponse().getContentAsString();
        long warehouseId = objectMapper.readTree(whBody).get("id").asLong();

        mockMvc.perform(post("/api/warehouses/" + warehouseId + "/stock").header(AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StockAdjustmentRequest(productId, 50))));

        User customer = createUser(Role.CUSTOMER);
        mockMvc.perform(post("/api/cart/items").header(AUTHORIZATION, bearer(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new AddCartItemRequest(productId, qty))));

        String orderBody = mockMvc.perform(post("/api/checkout").header(AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CheckoutRequest(null, null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(orderBody).get("id").asLong();
        return new Placed(orderId, customer.getId(), warehouseId);
    }

    /** Wait until the async pipeline has confirmed the order. */
    private void awaitConfirmed(Placed placed, String admin) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/orders/" + placed.orderId()).header(AUTHORIZATION, admin))
                        .andExpect(jsonPath("$.status").value("CONFIRMED")));
    }

    @Test
    void asyncPipelineConfirmsOrderAndWritesNotificationAndAudit() throws Exception {
        String admin = loginAs(Role.ADMIN);
        Placed placed = placeOrder(admin, 1);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(notificationRepository.findByCustomerIdOrderByIdAsc(placed.customerId()))
                    .anyMatch(n -> n.getType().equals("ORDER_PLACED") && n.getStatus().equals("SENT"));
            assertThat(auditLogRepository
                    .findByEntityTypeAndEntityIdOrderByIdAsc("Order", placed.orderId()))
                    .anyMatch(a -> a.getAction().equals("ORDER_PLACED"));
            assertThat(shipmentRepository.findByOrderId(placed.orderId())).isNotEmpty();
        });

        mockMvc.perform(get("/api/orders/" + placed.orderId()).header(AUTHORIZATION, admin))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void staffDrivesOrderThroughToDelivered() throws Exception {
        String admin = loginAs(Role.ADMIN);
        String staff = loginAs(Role.WAREHOUSE_STAFF);
        Placed placed = placeOrder(admin, 1);
        awaitConfirmed(placed, admin);

        advance(staff, placed.orderId(), OrderStatus.PACKED).andExpect(jsonPath("$.status").value("PACKED"));
        advance(staff, placed.orderId(), OrderStatus.SHIPPED).andExpect(jsonPath("$.status").value("SHIPPED"));
        advance(staff, placed.orderId(), OrderStatus.DELIVERED).andExpect(jsonPath("$.status").value("DELIVERED"));

        assertThat(shipmentRepository.findByOrderId(placed.orderId()))
                .allMatch(s -> s.getStatus() == ShipmentStatus.DELIVERED);
    }

    @Test
    void skippingAStepIsRejected() throws Exception {
        String admin = loginAs(Role.ADMIN);
        String staff = loginAs(Role.WAREHOUSE_STAFF);
        Placed placed = placeOrder(admin, 1);
        awaitConfirmed(placed, admin);

        // CONFIRMED -> DELIVERED is not a legal edge.
        advance(staff, placed.orderId(), OrderStatus.DELIVERED)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_TRANSITION"));
    }

    @Test
    void customerCannotDriveFulfillment() throws Exception {
        String admin = loginAs(Role.ADMIN);
        Placed placed = placeOrder(admin, 1);
        awaitConfirmed(placed, admin);

        mockMvc.perform(post("/api/orders/" + placed.orderId() + "/fulfillment/status")
                        .header(AUTHORIZATION, loginAs(Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new FulfillmentStatusRequest(OrderStatus.PACKED))))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffUpdatesShipmentTracking() throws Exception {
        String admin = loginAs(Role.ADMIN);
        String staff = loginAs(Role.WAREHOUSE_STAFF);
        Placed placed = placeOrder(admin, 1);
        awaitConfirmed(placed, admin);
        long shipmentId = shipmentRepository.findByOrderId(placed.orderId()).get(0).getId();

        mockMvc.perform(put("/api/orders/" + placed.orderId() + "/shipments/" + shipmentId + "/tracking")
                        .header(AUTHORIZATION, staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new TrackingUpdateRequest("UPS", "1Z-TRACK-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carrier").value("UPS"))
                .andExpect(jsonPath("$.trackingNumber").value("1Z-TRACK-123"));
    }

    private org.springframework.test.web.servlet.ResultActions advance(
            String staff, long orderId, OrderStatus target) throws Exception {
        return mockMvc.perform(post("/api/orders/" + orderId + "/fulfillment/status")
                .header(AUTHORIZATION, staff)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new FulfillmentStatusRequest(target))));
    }
}
