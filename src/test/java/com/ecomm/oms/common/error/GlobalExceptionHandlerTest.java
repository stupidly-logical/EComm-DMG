package com.ecomm.oms.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the RFC-7807 mapping performed by {@link GlobalExceptionHandler}.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsApiExceptionToItsStatusCodeAndDetail() {
        ProblemDetail pd = handler.handleApiException(NotFoundException.of("Product", 42L));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(pd.getDetail()).isEqualTo("Product 42 not found");
        assertThat(pd.getProperties()).containsEntry("code", "NOT_FOUND");
        assertThat(pd.getProperties()).containsKey("timestamp");
    }

    @Test
    void mapsConflictWithCustomErrorCode() {
        ProblemDetail pd = handler.handleApiException(
                new ConflictException("Only 1 unit available", "INSUFFICIENT_STOCK"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(pd.getProperties()).containsEntry("code", "INSUFFICIENT_STOCK");
    }

    @Test
    void mapsBusinessRuleToUnprocessableEntity() {
        ProblemDetail pd = handler.handleApiException(
                new BusinessRuleException("Order below coupon minimum"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(pd.getProperties()).containsEntry("code", "BUSINESS_RULE_VIOLATION");
    }

    @Test
    void catchAllHidesInternalDetailAndReturns500() {
        ProblemDetail pd = handler.handleUnexpected(new RuntimeException("boom: secret stacktrace"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(pd.getDetail()).isEqualTo("An unexpected error occurred");
        assertThat(pd.getDetail()).doesNotContain("secret");
    }
}
