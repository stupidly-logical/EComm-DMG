package com.ecomm.oms.user;

import com.ecomm.oms.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC enforcement on a method-secured admin probe endpoint ({@code GET /api/users}):
 * anonymous → 401, wrong role → 403, correct role → 200.
 */
class RbacIT extends IntegrationTestSupport {

    @Test
    void anonymousGets401() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void customerGets403() throws Exception {
        mockMvc.perform(get("/api/users").header(AUTHORIZATION, loginAs(Role.CUSTOMER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void warehouseStaffGets403() throws Exception {
        mockMvc.perform(get("/api/users").header(AUTHORIZATION, loginAs(Role.WAREHOUSE_STAFF)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminGets200() throws Exception {
        User admin = createUser(Role.ADMIN);
        mockMvc.perform(get("/api/users").header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == '" + admin.getEmail() + "')]").exists());
    }
}
