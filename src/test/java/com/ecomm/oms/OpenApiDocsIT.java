package com.ecomm.oms;

import com.ecomm.oms.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the generated OpenAPI document: it builds, exposes the bearer scheme and the
 * reusable error responses, marks public operations as unsecured, and lets secured operations
 * inherit the global bearer requirement. Guards against annotation drift breaking the docs.
 */
class OpenApiDocsIT extends IntegrationTestSupport {

    @Test
    void apiDocsGenerateWithSecurityAndReusableResponses() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // Document shell + global bearer requirement.
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                // Reusable error components are present and referenceable.
                .andExpect(jsonPath("$.components.schemas.ProblemDetail").exists())
                .andExpect(jsonPath("$.components.responses.NotFound").exists())
                .andExpect(jsonPath("$.components.responses.Unauthorized").exists())
                // Representative paths exist.
                .andExpect(jsonPath("$.paths['/api/checkout'].post").exists())
                .andExpect(jsonPath("$.paths['/api/products']").exists())
                // Public op opts out of security (explicit empty array).
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty())
                // Secured op inherits the global requirement (no per-op override).
                .andExpect(jsonPath("$.paths['/api/checkout'].post.security").doesNotExist())
                // Documented error responses are wired onto operations.
                .andExpect(jsonPath("$.paths['/api/checkout'].post.responses.402").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].get.responses.404").exists());
    }

    @Test
    void swaggerUiIsAvailable() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
