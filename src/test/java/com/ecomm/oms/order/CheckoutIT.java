package com.ecomm.oms.order;

import com.ecomm.oms.cart.dto.AddCartItemRequest;
import com.ecomm.oms.catalog.dto.ProductRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checkout flows over HTTP: happy path with stock decrement, payment-failure rollback,
 * insufficient stock, idempotent replay, and order-ownership visibility.
 */
class CheckoutIT extends IntegrationTestSupport {

    @Autowired
    private StockLevelRepository stockLevelRepository;

    private record Sku(long productId, long warehouseId) {
    }

    /** Create a product and a warehouse stocked with {@code onHand} units. */
    private Sku stockedProduct(String admin, String price, int onHand) throws Exception {
        String productBody = mockMvc.perform(post("/api/products").header(AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ProductRequest("CK-" + System.nanoTime(), "Checkout Item",
                                null, new BigDecimal(price), "NONE-" + System.nanoTime(), true, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long productId = objectMapper.readTree(productBody).get("id").asLong();

        String whBody = mockMvc.perform(post("/api/warehouses").header(AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WarehouseRequest("WH-" + System.nanoTime(), "W", "US", 1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long warehouseId = objectMapper.readTree(whBody).get("id").asLong();

        mockMvc.perform(post("/api/warehouses/" + warehouseId + "/stock").header(AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new StockAdjustmentRequest(productId, onHand))))
                .andExpect(status().isOk());
        return new Sku(productId, warehouseId);
    }

    private void addToCart(User customer, long productId, int qty) throws Exception {
        mockMvc.perform(post("/api/cart/items").header(AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new AddCartItemRequest(productId, qty))))
                .andExpect(status().isOk());
    }

    private int onHand(Sku sku) {
        return stockLevelRepository.findByProductIdAndWarehouseId(sku.productId(), sku.warehouseId())
                .map(StockLevel::getQuantityOnHand).orElseThrow();
    }

    private int reserved(Sku sku) {
        return stockLevelRepository.findByProductIdAndWarehouseId(sku.productId(), sku.warehouseId())
                .map(StockLevel::getQuantityReserved).orElseThrow();
    }

    @Test
    void happyPathPlacesOrderAndDecrementsStock() throws Exception {
        String admin = loginAs(Role.ADMIN);
        Sku sku = stockedProduct(admin, "50.00", 10);
        User customer = createUser(Role.CUSTOMER);
        addToCart(customer, sku.productId(), 2);

        mockMvc.perform(post("/api/checkout").header(AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CheckoutRequest(null, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.grandTotal").value(100.00))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].allocatedWarehouseId").value(sku.warehouseId()));

        assertThat(onHand(sku)).isEqualTo(8);
        assertThat(reserved(sku)).isZero();
    }

    @Test
    void paymentFailureRollsBackWithNoStockChangeAndNoOrder() throws Exception {
        String admin = loginAs(Role.ADMIN);
        Sku sku = stockedProduct(admin, "50.00", 10);
        User customer = createUser(Role.CUSTOMER);
        addToCart(customer, sku.productId(), 3);

        mockMvc.perform(post("/api/checkout").header(AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CheckoutRequest(null, null, "DECLINE"))))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_DECLINED"));

        // No stock decremented and no reservation left behind.
        assertThat(onHand(sku)).isEqualTo(10);
        assertThat(reserved(sku)).isZero();
        // No order persisted for the customer.
        mockMvc.perform(get("/api/orders").header(AUTHORIZATION, bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void insufficientStockIsRejected() throws Exception {
        String admin = loginAs(Role.ADMIN);
        Sku sku = stockedProduct(admin, "5.00", 1);
        User customer = createUser(Role.CUSTOMER);
        addToCart(customer, sku.productId(), 5);

        mockMvc.perform(post("/api/checkout").header(AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CheckoutRequest(null, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertThat(onHand(sku)).isEqualTo(1);
        assertThat(reserved(sku)).isZero();
    }

    @Test
    void emptyCartCannotCheckout() throws Exception {
        User customer = createUser(Role.CUSTOMER);
        // Creating the cart without items via the view endpoint.
        mockMvc.perform(get("/api/cart").header(AUTHORIZATION, bearer(customer)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/checkout").header(AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CheckoutRequest(null, null, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));
    }

    @Test
    void duplicateIdempotencyKeyReplaysTheSameOrder() throws Exception {
        String admin = loginAs(Role.ADMIN);
        Sku sku = stockedProduct(admin, "20.00", 10);
        User customer = createUser(Role.CUSTOMER);
        addToCart(customer, sku.productId(), 1);
        String key = "idem-" + System.nanoTime();

        String first = mockMvc.perform(post("/api/checkout").header(AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CheckoutRequest(key, null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long firstId = objectMapper.readTree(first).get("id").asLong();
        assertThat(onHand(sku)).isEqualTo(9);

        // Replaying the same key returns the original order and does not decrement again.
        String second = mockMvc.perform(post("/api/checkout").header(AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CheckoutRequest(key, null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long secondId = objectMapper.readTree(second).get("id").asLong();

        assertThat(secondId).isEqualTo(firstId);
        assertThat(onHand(sku)).isEqualTo(9);
    }

    @Test
    void customerCannotSeeAnotherCustomersOrder() throws Exception {
        String admin = loginAs(Role.ADMIN);
        Sku sku = stockedProduct(admin, "10.00", 10);
        User owner = createUser(Role.CUSTOMER);
        addToCart(owner, sku.productId(), 1);
        String body = mockMvc.perform(post("/api/checkout").header(AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CheckoutRequest(null, null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(body).get("id").asLong();

        // A different customer is hidden from it (404).
        mockMvc.perform(get("/api/orders/" + orderId).header(AUTHORIZATION, loginAs(Role.CUSTOMER)))
                .andExpect(status().isNotFound());
        // The owner can see it.
        mockMvc.perform(get("/api/orders/" + orderId).header(AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());
        // An admin can see it.
        mockMvc.perform(get("/api/orders/" + orderId).header(AUTHORIZATION, admin))
                .andExpect(status().isOk());
    }
}
