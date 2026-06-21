package com.ecomm.oms.returns;

import com.ecomm.oms.cart.dto.AddCartItemRequest;
import com.ecomm.oms.catalog.dto.ProductRequest;
import com.ecomm.oms.fulfillment.dto.FulfillmentStatusRequest;
import com.ecomm.oms.inventory.StockLevel;
import com.ecomm.oms.inventory.StockLevelRepository;
import com.ecomm.oms.inventory.dto.StockAdjustmentRequest;
import com.ecomm.oms.inventory.dto.WarehouseRequest;
import com.ecomm.oms.order.OrderStatus;
import com.ecomm.oms.order.dto.CheckoutRequest;
import com.ecomm.oms.returns.dto.CreateReturnRequest;
import com.ecomm.oms.support.IntegrationTestSupport;
import com.ecomm.oms.user.Role;
import com.ecomm.oms.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Returns and refunds: full request → approve → refund → restock, plus the guard rails.
 */
class ReturnIT extends IntegrationTestSupport {

    @Autowired
    private StockLevelRepository stockLevelRepository;

    private record Ctx(long orderId, long orderItemId, long productId, long warehouseId, User customer) {
    }

    /** Place an order and (optionally) drive it all the way to DELIVERED. */
    private Ctx order(String admin, int onHand, int qty, String price, boolean deliver) throws Exception {
        String productBody = mockMvc.perform(post("/api/products").header(AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ProductRequest("RT-" + System.nanoTime(), "Item", null,
                                new BigDecimal(price), "NONE-" + System.nanoTime(), true, null))))
                .andReturn().getResponse().getContentAsString();
        long productId = objectMapper.readTree(productBody).get("id").asLong();
        String whBody = mockMvc.perform(post("/api/warehouses").header(AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WarehouseRequest("RW-" + System.nanoTime(), "W", "US", 1))))
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
        long orderItemId = objectMapper.readTree(orderBody).get("items").get(0).get("id").asLong();

        if (deliver) {
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    mockMvc.perform(get("/api/orders/" + orderId).header(AUTHORIZATION, admin))
                            .andExpect(jsonPath("$.status").value("CONFIRMED")));
            advance(admin, orderId, OrderStatus.PACKED);
            advance(admin, orderId, OrderStatus.SHIPPED);
            advance(admin, orderId, OrderStatus.DELIVERED);
        }
        return new Ctx(orderId, orderItemId, productId, warehouseId, customer);
    }

    private void advance(String admin, long orderId, OrderStatus target) throws Exception {
        // The seeded warehouse-staff path; here driven via a freshly-created staff token.
        String staff = loginAs(Role.WAREHOUSE_STAFF);
        mockMvc.perform(post("/api/orders/" + orderId + "/fulfillment/status").header(AUTHORIZATION, staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new FulfillmentStatusRequest(target))))
                .andExpect(status().isOk());
    }

    private int onHand(Ctx ctx) {
        return stockLevelRepository.findByProductIdAndWarehouseId(ctx.productId(), ctx.warehouseId())
                .map(StockLevel::getQuantityOnHand).orElseThrow();
    }

    @Test
    void requestApproveRefundAndRestock() throws Exception {
        String admin = loginAs(Role.ADMIN);
        Ctx ctx = order(admin, 10, 2, "50.00", true);
        assertThat(onHand(ctx)).isEqualTo(8);

        String returnBody = mockMvc.perform(post("/api/orders/" + ctx.orderId() + "/returns")
                        .header(AUTHORIZATION, bearer(ctx.customer()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CreateReturnRequest("Changed my mind",
                                List.of(new CreateReturnRequest.Line(ctx.orderItemId(), 2, "n/a"))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andReturn().getResponse().getContentAsString();
        long returnId = objectMapper.readTree(returnBody).get("id").asLong();

        mockMvc.perform(post("/api/returns/" + returnId + "/approve").header(AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.refund.amount").value(100.00))
                .andExpect(jsonPath("$.refund.status").value("COMPLETED"));

        // Order moved to RETURNED and the two units are back on the shelf.
        mockMvc.perform(get("/api/orders/" + ctx.orderId()).header(AUTHORIZATION, admin))
                .andExpect(jsonPath("$.status").value("RETURNED"));
        assertThat(onHand(ctx)).isEqualTo(10);
    }

    @Test
    void cannotReturnAnUndeliveredOrder() throws Exception {
        String admin = loginAs(Role.ADMIN);
        Ctx ctx = order(admin, 10, 1, "50.00", false);

        mockMvc.perform(post("/api/orders/" + ctx.orderId() + "/returns")
                        .header(AUTHORIZATION, bearer(ctx.customer()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CreateReturnRequest("too soon",
                                List.of(new CreateReturnRequest.Line(ctx.orderItemId(), 1, null))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_DELIVERED"));
    }

    @Test
    void cannotReturnMoreThanOrdered() throws Exception {
        String admin = loginAs(Role.ADMIN);
        Ctx ctx = order(admin, 10, 1, "50.00", true);

        mockMvc.perform(post("/api/orders/" + ctx.orderId() + "/returns")
                        .header(AUTHORIZATION, bearer(ctx.customer()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CreateReturnRequest(null,
                                List.of(new CreateReturnRequest.Line(ctx.orderItemId(), 5, null))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RETURN_QTY_EXCEEDS"));
    }

    @Test
    void customerCannotApproveOwnReturn() throws Exception {
        String admin = loginAs(Role.ADMIN);
        Ctx ctx = order(admin, 10, 1, "50.00", true);
        String returnBody = mockMvc.perform(post("/api/orders/" + ctx.orderId() + "/returns")
                        .header(AUTHORIZATION, bearer(ctx.customer()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CreateReturnRequest(null,
                                List.of(new CreateReturnRequest.Line(ctx.orderItemId(), 1, null))))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long returnId = objectMapper.readTree(returnBody).get("id").asLong();

        mockMvc.perform(post("/api/returns/" + returnId + "/approve")
                        .header(AUTHORIZATION, bearer(ctx.customer())))
                .andExpect(status().isForbidden());
    }
}
