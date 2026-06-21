package com.ecomm.oms.catalog;

import com.ecomm.oms.catalog.dto.CategoryRequest;
import com.ecomm.oms.catalog.dto.ProductRequest;
import com.ecomm.oms.support.IntegrationTestSupport;
import com.ecomm.oms.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Catalog admin CRUD, public browse/search/pagination, and write-side RBAC.
 */
class CatalogIT extends IntegrationTestSupport {

    private long createCategory(String adminAuth, String name) throws Exception {
        String body = mockMvc.perform(post("/api/categories")
                        .header(AUTHORIZATION, adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CategoryRequest(name, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private long createProduct(String adminAuth, ProductRequest req) throws Exception {
        String body = mockMvc.perform(post("/api/products")
                        .header(AUTHORIZATION, adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value(req.sku()))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void adminCreatesCategoryAndProductThenPublicCanRead() throws Exception {
        String admin = loginAs(Role.ADMIN);
        long categoryId = createCategory(admin, "Electronics-" + System.nanoTime());
        String sku = "SKU-" + System.nanoTime();
        long productId = createProduct(admin, new ProductRequest(
                sku, "Widget", "A widget", new BigDecimal("19.99"), "STANDARD", true, categoryId));

        // Public (no auth) can read the product.
        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value(sku))
                .andExpect(jsonPath("$.categoryId").value(categoryId));
    }

    @Test
    void browseFiltersBySearchTermAndPaginates() throws Exception {
        String admin = loginAs(Role.ADMIN);
        String marker = "ZZ" + System.nanoTime();
        for (int i = 0; i < 3; i++) {
            createProduct(admin, new ProductRequest(
                    "SKU-" + marker + "-" + i, marker + " Gadget " + i, null,
                    new BigDecimal("5.00"), "STANDARD", true, null));
        }

        // q matches all three; page size 2 returns 2 of 3 with totalElements=3.
        mockMvc.perform(get("/api/products")
                        .param("q", marker)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void inactiveProductsAreHiddenFromBrowse() throws Exception {
        String admin = loginAs(Role.ADMIN);
        String marker = "HID" + System.nanoTime();
        createProduct(admin, new ProductRequest(
                "SKU-" + marker, marker + " Hidden", null,
                new BigDecimal("9.00"), "STANDARD", false, null));

        mockMvc.perform(get("/api/products").param("q", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void customerCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header(AUTHORIZATION, loginAs(Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ProductRequest(
                                "SKU-x", "x", null, new BigDecimal("1.00"), "STANDARD", true, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ProductRequest(
                                "SKU-y", "y", null, new BigDecimal("1.00"), "STANDARD", true, null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateSkuIsRejected() throws Exception {
        String admin = loginAs(Role.ADMIN);
        String sku = "DUP-" + System.nanoTime();
        var req = new ProductRequest(sku, "Dup", null, new BigDecimal("1.00"), "STANDARD", true, null);
        createProduct(admin, req);

        mockMvc.perform(post("/api/products")
                        .header(AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKU_TAKEN"));
    }

    @Test
    void invalidProductPayloadIsRejected() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header(AUTHORIZATION, loginAs(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ProductRequest(
                                "", "", null, new BigDecimal("-1"), "STANDARD", true, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void deleteProductThenItIsGone() throws Exception {
        String admin = loginAs(Role.ADMIN);
        long id = createProduct(admin, new ProductRequest(
                "DEL-" + System.nanoTime(), "Del", null, new BigDecimal("2.00"), "STANDARD", true, null));

        mockMvc.perform(delete("/api/products/" + id).header(AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
