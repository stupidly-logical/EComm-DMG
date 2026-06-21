package com.ecomm.oms.cart;

import com.ecomm.oms.cart.dto.AddCartItemRequest;
import com.ecomm.oms.cart.dto.ApplyCouponRequest;
import com.ecomm.oms.cart.dto.CartResponse;
import com.ecomm.oms.cart.dto.UpdateCartItemRequest;
import com.ecomm.oms.security.AuthPrincipal;
import com.ecomm.oms.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "Cart", description = "The authenticated customer's shopping cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    @Operation(summary = "View the current cart with priced totals")
    @ApiResponses({
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")})
    public CartResponse view(@CurrentUser AuthPrincipal me) {
        return cartService.view(me.userId());
    }

    @PostMapping("/items")
    @Operation(summary = "Add a product to the cart (increments if already present)")
    @ApiResponses({
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/UnprocessableEntity")})
    public CartResponse addItem(@CurrentUser AuthPrincipal me,
                                @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(me.userId(), request);
    }

    @PutMapping("/items/{productId}")
    @Operation(summary = "Set the quantity of a product already in the cart")
    @ApiResponses({
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")})
    public CartResponse updateItem(@CurrentUser AuthPrincipal me,
                                   @Parameter(description = "Product id of the cart line") @PathVariable Long productId,
                                   @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItem(me.userId(), productId, request);
    }

    @DeleteMapping("/items/{productId}")
    @Operation(summary = "Remove a product from the cart")
    @ApiResponses({
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")})
    public CartResponse removeItem(@CurrentUser AuthPrincipal me,
                                   @Parameter(description = "Product id of the cart line") @PathVariable Long productId) {
        return cartService.removeItem(me.userId(), productId);
    }

    @PostMapping("/apply-coupon")
    @Operation(summary = "Apply a coupon code to the cart")
    @ApiResponses({
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/UnprocessableEntity")})
    public CartResponse applyCoupon(@CurrentUser AuthPrincipal me,
                                    @Valid @RequestBody ApplyCouponRequest request) {
        return cartService.applyCoupon(me.userId(), request);
    }

    @DeleteMapping("/coupon")
    @Operation(summary = "Remove the applied coupon from the cart")
    @ApiResponses({
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")})
    public CartResponse removeCoupon(@CurrentUser AuthPrincipal me) {
        return cartService.removeCoupon(me.userId());
    }
}
