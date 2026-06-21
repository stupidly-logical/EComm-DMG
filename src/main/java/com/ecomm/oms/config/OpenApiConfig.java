package com.ecomm.oms.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document metadata, a global HTTP-bearer (JWT) security scheme, and reusable
 * components so controllers can document errors tersely.
 *
 * <ul>
 *   <li>The {@code bearerAuth} scheme is required globally, so secured endpoints need no
 *       per-operation security annotation; public endpoints opt out with an empty
 *       {@code @SecurityRequirements}.</li>
 *   <li>A {@code ProblemDetail} schema mirrors the RFC-7807 body from
 *       {@code GlobalExceptionHandler}, and reusable {@code responses} ({@code Unauthorized},
 *       {@code Forbidden}, {@code NotFound}, {@code Conflict}, {@code UnprocessableEntity},
 *       {@code BadRequest}) reference it. Controllers attach them with
 *       {@code @ApiResponse(ref = "#/components/responses/<Name>")}.</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";
    private static final String PROBLEM_SCHEMA = "ProblemDetail";
    private static final String PROBLEM_JSON = "application/problem+json";

    @Bean
    public OpenAPI omsOpenAPI() {
        Components components = new Components()
                .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT obtained from POST /api/auth/login or /register"))
                .addSchemas(PROBLEM_SCHEMA, problemDetailSchema())
                .addResponses("BadRequest", problemResponse("Invalid request (validation or malformed body)"))
                .addResponses("Unauthorized", problemResponse("Authentication is required or the token is invalid"))
                .addResponses("Forbidden", problemResponse("Authenticated but lacking the required role/ownership"))
                .addResponses("NotFound", problemResponse("The referenced resource does not exist"))
                .addResponses("Conflict", problemResponse("Conflicts with current state (duplicate, illegal transition, insufficient stock)"))
                .addResponses("PaymentRequired", problemResponse("The payment was declined"))
                .addResponses("UnprocessableEntity", problemResponse("A business rule was violated"));

        return new OpenAPI()
                .info(new Info()
                        .title("E-Commerce Order Management System API")
                        .description("Catalog, cart, checkout, fulfillment, and returns "
                                + "behind JWT role-based access control. Errors follow RFC-7807 "
                                + "(application/problem+json) with a stable `code`.")
                        .version("0.1.0")
                        .license(new License().name("Proprietary")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(components);
    }

    /**
     * springdoc rebuilds {@code components.schemas} from scanned model types, which drops
     * schemas registered directly on the {@link OpenAPI} bean. An {@link OpenApiCustomizer}
     * runs after that scan, so it is the reliable place to (re-)add the shared
     * {@code ProblemDetail} schema that the reusable error responses reference.
     */
    @Bean
    public OpenApiCustomizer problemDetailSchemaCustomizer() {
        return openApi -> {
            if (openApi.getComponents() != null) {
                openApi.getComponents().addSchemas(PROBLEM_SCHEMA, problemDetailSchema());
            }
        };
    }

    /** RFC-7807 envelope as produced by {@code GlobalExceptionHandler}. */
    private Schema<?> problemDetailSchema() {
        return new Schema<>()
                .type("object")
                .description("RFC-7807 problem detail with a stable machine-readable code")
                .addProperty("type", new Schema<>().type("string").example("about:blank"))
                .addProperty("title", new Schema<>().type("string").example("Not Found"))
                .addProperty("status", new Schema<>().type("integer").format("int32").example(404))
                .addProperty("detail", new Schema<>().type("string").example("Product 42 not found"))
                .addProperty("instance", new Schema<>().type("string").example("/api/products/42"))
                .addProperty("code", new Schema<>().type("string").example("NOT_FOUND"))
                .addProperty("timestamp", new Schema<>().type("string").format("date-time"));
    }

    private ApiResponse problemResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(PROBLEM_JSON,
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA))));
    }
}
