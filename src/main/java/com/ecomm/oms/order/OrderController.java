package com.ecomm.oms.order;

import com.ecomm.oms.order.dto.CheckoutRequest;
import com.ecomm.oms.order.dto.OrderResponse;
import com.ecomm.oms.security.AuthPrincipal;
import com.ecomm.oms.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Orders", description = "Checkout and order tracking")
public class OrderController {

    private final CheckoutService checkoutService;
    private final OrderService orderService;

    public OrderController(CheckoutService checkoutService, OrderService orderService) {
        this.checkoutService = checkoutService;
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Place an order from the active cart (atomic; reserves stock + charges payment)")
    @ApiResponses({
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "402", ref = "#/components/responses/PaymentRequired"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/UnprocessableEntity")})
    public OrderResponse checkout(@CurrentUser AuthPrincipal me,
                                  @Valid @RequestBody CheckoutRequest request) {
        return checkoutService.placeOrder(me.userId(), request);
    }

    @GetMapping("/orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "List the authenticated customer's orders")
    @ApiResponses({
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")})
    public List<OrderResponse> myOrders(@CurrentUser AuthPrincipal me) {
        return orderService.listForCustomer(me.userId());
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Get an order (own order for customers; any for admin/warehouse staff)")
    @ApiResponses({
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")})
    public OrderResponse getOrder(@CurrentUser AuthPrincipal me,
                                  @Parameter(description = "Order id") @PathVariable Long id) {
        return orderService.getForPrincipal(id, me);
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Operation(summary = "Cancel an order and restock (only before it ships)")
    @ApiResponses({
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")})
    public OrderResponse cancel(@CurrentUser AuthPrincipal me,
                                @Parameter(description = "Order id") @PathVariable Long id) {
        return orderService.cancel(id, me);
    }
}
