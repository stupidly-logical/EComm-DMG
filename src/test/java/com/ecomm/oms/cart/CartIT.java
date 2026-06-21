package com.ecomm.oms.cart;

import com.ecomm.oms.cart.dto.AddCartItemRequest;
import com.ecomm.oms.cart.dto.ApplyCouponRequest;
import com.ecomm.oms.cart.dto.UpdateCartItemRequest;
import com.ecomm.oms.catalog.dto.ProductRequest;
import com.ecomm.oms.pricing.CouponType;
import com.ecomm.oms.pricing.dto.CouponRequest;
import com.ecomm.oms.pricing.dto.TaxRateRequest;
import com.ecomm.oms.support.IntegrationTestSupport;
import com.ecomm.oms.user.Role;
import com.ecomm.oms.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cart lifecycle: add/update/remove, end-to-end tax computation, coupon apply/validation,
 * and customer-only RBAC.
 */
class CartIT extends IntegrationTestSupport {

    private long createProduct(String admin, String taxCategory, String price) throws Exception {
        String body = mockMvc.perform(post("/api/products")
                        .header(AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ProductRequest(
                                "CART-" + System.nanoTime(), "Cart Product", null,
                                new BigDecimal(price), taxCategory, true, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private void createTaxRate(String admin, String taxCategory, String ratePercent) throws Exception {
        mockMvc.perform(post("/api/tax-rates")
                        .header(AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new TaxRateRequest(taxCategory, "US", new BigDecimal(ratePercent)))))
                .andExpect(status().isCreated());
    }

    @Test
    void addItemComputesSubtotalAndTax() throws Exception {
        String admin = loginAs(Role.ADMIN);
        String taxCat = "TX" + System.nanoTime();
        createTaxRate(admin, taxCat, "10.000");
        long productId = createProduct(admin, taxCat, "100.00");
        String customer = bearer(createUser(Role.CUSTOMER));

        mockMvc.perform(post("/api/cart/items")
                        .header(AUTHORIZATION, customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new AddCartItemRequest(productId, 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.subtotal").value(200.00))
                .andExpect(jsonPath("$.taxTotal").value(20.00))
                .andExpect(jsonPath("$.grandTotal").value(220.00));
    }

    @Test
    void addingSameProductIncrementsQuantityThenUpdateAndRemove() throws Exception {
        String admin = loginAs(Role.ADMIN);
        long productId = createProduct(admin, "NOTAX" + System.nanoTime(), "10.00");
        User customerUser = createUser(Role.CUSTOMER);
        String customer = bearer(customerUser);

        mockMvc.perform(post("/api/cart/items").header(AUTHORIZATION, customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new AddCartItemRequest(productId, 1))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/cart/items").header(AUTHORIZATION, bearer(customerUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new AddCartItemRequest(productId, 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.subtotal").value(30.00));

        // Set absolute quantity.
        mockMvc.perform(put("/api/cart/items/" + productId).header(AUTHORIZATION, bearer(customerUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new UpdateCartItemRequest(5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(5))
                .andExpect(jsonPath("$.subtotal").value(50.00));

        // Remove.
        mockMvc.perform(delete("/api/cart/items/" + productId).header(AUTHORIZATION, bearer(customerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.grandTotal").value(0.00));
    }

    @Test
    void applyValidPercentCouponReducesTotal() throws Exception {
        String admin = loginAs(Role.ADMIN);
        long productId = createProduct(admin, "NOTAX" + System.nanoTime(), "100.00");
        String code = "SAVE" + System.nanoTime();
        mockMvc.perform(post("/api/coupons").header(AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CouponRequest(code, CouponType.PERCENT,
                                new BigDecimal("10"), BigDecimal.ZERO, null, null, null, true))))
                .andExpect(status().isCreated());

        User customerUser = createUser(Role.CUSTOMER);
        mockMvc.perform(post("/api/cart/items").header(AUTHORIZATION, bearer(customerUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new AddCartItemRequest(productId, 1))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/cart/apply-coupon").header(AUTHORIZATION, bearer(customerUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ApplyCouponRequest(code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCouponCode").value(code))
                .andExpect(jsonPath("$.discountTotal").value(10.00))
                .andExpect(jsonPath("$.grandTotal").value(90.00));
    }

    @Test
    void couponBelowMinimumIsRejected() throws Exception {
        String admin = loginAs(Role.ADMIN);
        long productId = createProduct(admin, "NOTAX" + System.nanoTime(), "10.00");
        String code = "BIG" + System.nanoTime();
        mockMvc.perform(post("/api/coupons").header(AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CouponRequest(code, CouponType.FIXED,
                                new BigDecimal("5"), new BigDecimal("1000"), null, null, null, true))))
                .andExpect(status().isCreated());

        User customerUser = createUser(Role.CUSTOMER);
        mockMvc.perform(post("/api/cart/items").header(AUTHORIZATION, bearer(customerUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new AddCartItemRequest(productId, 1))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/cart/apply-coupon").header(AUTHORIZATION, bearer(customerUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ApplyCouponRequest(code))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COUPON_MIN_NOT_MET"));
    }

    @Test
    void unknownCouponIsNotFound() throws Exception {
        String admin = loginAs(Role.ADMIN);
        long productId = createProduct(admin, "NOTAX" + System.nanoTime(), "10.00");
        User customerUser = createUser(Role.CUSTOMER);
        mockMvc.perform(post("/api/cart/items").header(AUTHORIZATION, bearer(customerUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new AddCartItemRequest(productId, 1))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/cart/apply-coupon").header(AUTHORIZATION, bearer(customerUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ApplyCouponRequest("NOPE-" + System.nanoTime()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCannotUseCart() throws Exception {
        mockMvc.perform(get("/api/cart").header(AUTHORIZATION, loginAs(Role.ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotUseCart() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized());
    }
}
