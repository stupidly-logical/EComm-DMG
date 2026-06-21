package com.ecomm.oms.order;

import com.ecomm.oms.cart.dto.AddCartItemRequest;
import com.ecomm.oms.catalog.dto.ProductRequest;
import com.ecomm.oms.fulfillment.dto.FulfillmentStatusRequest;
import com.ecomm.oms.inventory.StockLevel;
import com.ecomm.oms.inventory.StockLevelRepository;
import com.ecomm.oms.inventory.dto.StockAdjustmentRequest;
import com.ecomm.oms.inventory.dto.WarehouseRequest;
import com.ecomm.oms.order.dto.CheckoutRequest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Order cancellation: restock on cancel, and rejection once an order has shipped.
 */
class CancelOrderIT extends IntegrationTestSupport {

    @Autowired
    private StockLevelRepository stockLevelRepository;

    private record Ctx(long orderId, long productId, long warehouseId, User customer) {
    }

    private Ctx place(String admin, int onHand, int qty) throws Exception {
        String productBody = mockMvc.perform(post("/api/products").header(AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ProductRequest("CN-" + System.nanoTime(), "Item", null,
                                new BigDecimal("10.00"), "NONE-" + System.nanoTime(), true, null))))
                .andReturn().getResponse().getContentAsString();
        long productId = objectMapper.readTree(productBody).get("id").asLong();
        String whBody = mockMvc.perform(post("/api/warehouses").header(AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WarehouseRequest("CW-" + System.nanoTime(), "W", "US", 1))))
                .andReturn().getResponse().getContentAsString();
        long warehouseId = objectMapper.readTree(whBody).get("id").asLong();
        mockMvc.perform(post("/api/warehouses/" + warehouseId + "/stock").header(AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StockAdjustmentRequest(productId, onHand))));

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
        return new Ctx(orderId, productId, warehouseId, customer);
    }

    private void awaitConfirmed(Ctx ctx, String admin) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/orders/" + ctx.orderId()).header(AUTHORIZATION, admin))
                        .andExpect(jsonPath("$.status").value("CONFIRMED")));
    }

    private int onHand(Ctx ctx) {
        return stockLevelRepository.findByProductIdAndWarehouseId(ctx.productId(), ctx.warehouseId())
                .map(StockLevel::getQuantityOnHand).orElseThrow();
    }

    @Test
    void cancellingRestocksInventory() throws Exception {
        String admin = loginAs(Role.ADMIN);
        Ctx ctx = place(admin, 10, 2);
        awaitConfirmed(ctx, admin); // let the pipeline settle to avoid a version race
        assertThat(onHand(ctx)).isEqualTo(8);

        mockMvc.perform(post("/api/orders/" + ctx.orderId() + "/cancel")
                        .header(AUTHORIZATION, bearer(ctx.customer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(onHand(ctx)).isEqualTo(10);
    }

    @Test
    void cannotCancelOnceShipped() throws Exception {
        String admin = loginAs(Role.ADMIN);
        String staff = loginAs(Role.WAREHOUSE_STAFF);
        Ctx ctx = place(admin, 10, 1);
        awaitConfirmed(ctx, admin);

        mockMvc.perform(post("/api/orders/" + ctx.orderId() + "/fulfillment/status").header(AUTHORIZATION, staff)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new FulfillmentStatusRequest(OrderStatus.PACKED))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + ctx.orderId() + "/fulfillment/status").header(AUTHORIZATION, staff)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new FulfillmentStatusRequest(OrderStatus.SHIPPED))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/" + ctx.orderId() + "/cancel")
                        .header(AUTHORIZATION, bearer(ctx.customer())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_TRANSITION"));
    }
}
