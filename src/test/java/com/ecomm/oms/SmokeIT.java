package com.ecomm.oms;

import com.ecomm.oms.cart.dto.AddCartItemRequest;
import com.ecomm.oms.cart.dto.ApplyCouponRequest;
import com.ecomm.oms.order.dto.CheckoutRequest;
import com.ecomm.oms.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end smoke over the V2 seed data: a seeded customer logs in, browses the public
 * catalog, builds a cart, applies the seeded coupon, and checks out against seeded stock.
 * Also confirms the seeded admin credentials work. Proves the seed migration and the whole
 * stack hang together.
 */
class SmokeIT extends IntegrationTestSupport {

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).get("token").asText();
    }

    private long seededProductId(String sku) throws Exception {
        String body = mockMvc.perform(get("/api/products").param("q", sku))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("content").get(0).get("id").asLong();
    }

    @Test
    void seededCustomerCanBrowseApplyCouponAndCheckout() throws Exception {
        String customer = login("customer@oms.local", "Customer123!");
        long headphones = seededProductId("SKU-HEADPHONES"); // 199.99, STANDARD tax

        mockMvc.perform(post("/api/cart/items").header(AUTHORIZATION, customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new AddCartItemRequest(headphones, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxTotal").value(16.50)); // 199.99 * 8.25% = 16.499 -> 16.50

        // Seeded WELCOME10 (10% off, min 20) applies.
        mockMvc.perform(post("/api/cart/apply-coupon").header(AUTHORIZATION, customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ApplyCouponRequest("WELCOME10"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountTotal").value(20.00)); // 10% of 199.99 -> 20.00

        mockMvc.perform(post("/api/checkout").header(AUTHORIZATION, customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CheckoutRequest(null, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.couponCode").value("WELCOME10"));
    }

    @Test
    void seededAdminCredentialsWork() throws Exception {
        String admin = login("admin@oms.local", "Admin123!");
        mockMvc.perform(get("/api/users").header(AUTHORIZATION, admin))
                .andExpect(status().isOk());
    }
}
