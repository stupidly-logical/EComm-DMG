package com.ecomm.oms.pricing;

import com.ecomm.oms.pricing.dto.CouponRequest;
import com.ecomm.oms.pricing.dto.CouponResponse;
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
@RequestMapping("/api/coupons")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Coupons", description = "Discount coupon administration (admin only)")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping
    @Operation(summary = "List coupons")
    @ApiResponses({
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")})
    public List<CouponResponse> list() {
        return couponService.list().stream().map(CouponResponse::from).toList();
    }

    @GetMapping("/{code}")
    @Operation(summary = "Get a coupon by code")
    @ApiResponses({
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")})
    public CouponResponse get(@Parameter(description = "Coupon code") @PathVariable String code) {
        return CouponResponse.from(couponService.getByCode(code));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a coupon")
    @ApiResponses({
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")})
    public CouponResponse create(@Valid @RequestBody CouponRequest request) {
        return CouponResponse.from(couponService.create(request));
    }
}
