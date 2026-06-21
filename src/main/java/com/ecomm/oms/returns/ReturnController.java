package com.ecomm.oms.returns;

import com.ecomm.oms.returns.dto.CreateReturnRequest;
import com.ecomm.oms.returns.dto.ReturnResponse;
import com.ecomm.oms.security.AuthPrincipal;
import com.ecomm.oms.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Returns", description = "Return requests, approval, and refunds")
public class ReturnController {

    private final ReturnService returnService;

    public ReturnController(ReturnService returnService) {
        this.returnService = returnService;
    }

    @PostMapping("/orders/{orderId}/returns")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Request a return for a delivered order")
    public ReturnResponse requestReturn(@CurrentUser AuthPrincipal me,
                                        @PathVariable Long orderId,
                                        @Valid @RequestBody CreateReturnRequest request) {
        return returnService.requestReturn(me, orderId, request);
    }

    @GetMapping("/returns/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Operation(summary = "Get a return request (own for customers; any for admin)")
    public ReturnResponse get(@CurrentUser AuthPrincipal me, @PathVariable Long id) {
        return returnService.get(id, me);
    }

    @PostMapping("/returns/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve a return: restock, issue refund, mark order RETURNED")
    public ReturnResponse approve(@CurrentUser AuthPrincipal me, @PathVariable Long id) {
        return returnService.approve(id, me.email());
    }

    @PostMapping("/returns/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reject a return request")
    public ReturnResponse reject(@CurrentUser AuthPrincipal me, @PathVariable Long id) {
        return returnService.reject(id, me.email());
    }
}
