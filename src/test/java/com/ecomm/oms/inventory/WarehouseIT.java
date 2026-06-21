package com.ecomm.oms.inventory;

import com.ecomm.oms.catalog.dto.ProductRequest;
import com.ecomm.oms.inventory.dto.StockAdjustmentRequest;
import com.ecomm.oms.inventory.dto.WarehouseRequest;
import com.ecomm.oms.support.IntegrationTestSupport;
import com.ecomm.oms.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Warehouse admin CRUD, stock adjustment, and admin-only RBAC.
 */
class WarehouseIT extends IntegrationTestSupport {

    private long createWarehouse(String adminAuth) throws Exception {
        String body = mockMvc.perform(post("/api/warehouses")
                        .header(AUTHORIZATION, adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WarehouseRequest(
                                "WH-" + System.nanoTime(), "Main", "US-EAST", 10))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value(10))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private long createProduct(String adminAuth) throws Exception {
        String body = mockMvc.perform(post("/api/products")
                        .header(AUTHORIZATION, adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ProductRequest(
                                "WP-" + System.nanoTime(), "Stocked", null,
                                new BigDecimal("3.00"), "STANDARD", true, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void adminAdjustsStockAndAvailabilityIsComputed() throws Exception {
        String admin = loginAs(Role.ADMIN);
        long warehouseId = createWarehouse(admin);
        long productId = createProduct(admin);

        mockMvc.perform(post("/api/warehouses/" + warehouseId + "/stock")
                        .header(AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new StockAdjustmentRequest(productId, 25))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityOnHand").value(25))
                .andExpect(jsonPath("$.quantityReserved").value(0))
                .andExpect(jsonPath("$.available").value(25));
    }

    @Test
    void adjustStockForMissingProductIs404() throws Exception {
        String admin = loginAs(Role.ADMIN);
        long warehouseId = createWarehouse(admin);

        mockMvc.perform(post("/api/warehouses/" + warehouseId + "/stock")
                        .header(AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new StockAdjustmentRequest(999_999L, 5))))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateWarehouseCodeIsRejected() throws Exception {
        String admin = loginAs(Role.ADMIN);
        var req = new WarehouseRequest("CODE-" + System.nanoTime(), "A", "R", 5);
        mockMvc.perform(post("/api/warehouses")
                        .header(AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/warehouses")
                        .header(AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WAREHOUSE_CODE_TAKEN"));
    }

    @Test
    void customerCannotAccessWarehouses() throws Exception {
        mockMvc.perform(get("/api/warehouses").header(AUTHORIZATION, loginAs(Role.CUSTOMER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotAccessWarehouses() throws Exception {
        mockMvc.perform(get("/api/warehouses"))
                .andExpect(status().isUnauthorized());
    }
}
