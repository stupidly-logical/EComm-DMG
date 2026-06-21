package com.ecomm.oms.cart;

import com.ecomm.oms.cart.dto.AddCartItemRequest;
import com.ecomm.oms.cart.dto.ApplyCouponRequest;
import com.ecomm.oms.cart.dto.CartResponse;
import com.ecomm.oms.cart.dto.UpdateCartItemRequest;
import com.ecomm.oms.catalog.Product;
import com.ecomm.oms.catalog.ProductRepository;
import com.ecomm.oms.common.error.BusinessRuleException;
import com.ecomm.oms.common.error.NotFoundException;
import com.ecomm.oms.pricing.Coupon;
import com.ecomm.oms.pricing.CouponService;
import com.ecomm.oms.pricing.PricedCart;
import com.ecomm.oms.pricing.PricingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages a customer's single active cart: item add/update/remove and coupon apply/remove.
 * Every mutating call returns the freshly re-priced {@link CartResponse} so the client always
 * sees current totals. Pricing/coupon validation is delegated so checkout reuses the same rules.
 */
@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final PricingService pricingService;
    private final CouponService couponService;

    public CartService(CartRepository cartRepository, ProductRepository productRepository,
                       PricingService pricingService, CouponService couponService) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.pricingService = pricingService;
        this.couponService = couponService;
    }

    @Transactional
    public CartResponse view(Long customerId) {
        return respond(getOrCreateActiveCart(customerId));
    }

    @Transactional
    public CartResponse addItem(Long customerId, AddCartItemRequest request) {
        Cart cart = getOrCreateActiveCart(customerId);
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> NotFoundException.of("Product", request.productId()));
        if (!product.isActive()) {
            throw new BusinessRuleException("Product is not available", "PRODUCT_INACTIVE");
        }
        cart.addOrIncrement(product, request.quantity(), product.getBasePrice());
        return respond(cart);
    }

    @Transactional
    public CartResponse updateItem(Long customerId, Long productId, UpdateCartItemRequest request) {
        Cart cart = getActiveCart(customerId);
        cart.findItem(productId)
                .orElseThrow(() -> new NotFoundException("Product " + productId + " is not in the cart"));
        cart.setItemQuantity(productId, request.quantity());
        return respond(cart);
    }

    @Transactional
    public CartResponse removeItem(Long customerId, Long productId) {
        Cart cart = getActiveCart(customerId);
        cart.removeItem(productId);
        return respond(cart);
    }

    @Transactional
    public CartResponse applyCoupon(Long customerId, ApplyCouponRequest request) {
        Cart cart = getActiveCart(customerId);
        if (cart.isEmpty()) {
            throw new BusinessRuleException("Cannot apply a coupon to an empty cart", "CART_EMPTY");
        }
        // Validate against the pre-discount subtotal; throws if the coupon cannot apply.
        Coupon coupon = couponService.validateForOrder(request.code(), pricingService.price(cart).subtotal());
        cart.setCouponCode(coupon.getCode());
        return respond(cart);
    }

    @Transactional
    public CartResponse removeCoupon(Long customerId) {
        Cart cart = getActiveCart(customerId);
        cart.clearCoupon();
        return respond(cart);
    }

    /** Used by checkout to read the caller's active cart. */
    @Transactional(readOnly = true)
    public Cart getActiveCart(Long customerId) {
        return cartRepository.findByCustomerIdAndStatus(customerId, CartStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("No active cart"));
    }

    private Cart getOrCreateActiveCart(Long customerId) {
        return cartRepository.findByCustomerIdAndStatus(customerId, CartStatus.ACTIVE)
                .orElseGet(() -> cartRepository.save(new Cart(customerId)));
    }

    private CartResponse respond(Cart cart) {
        PricedCart priced = pricingService.price(cart);
        return CartResponse.of(cart, priced);
    }
}
