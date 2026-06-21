package com.ecomm.oms.cart;

import com.ecomm.oms.catalog.Product;
import com.ecomm.oms.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A customer's shopping cart. At most one {@link CartStatus#ACTIVE} cart per customer is
 * maintained by the service layer; checkout transitions it to {@code CHECKED_OUT}.
 */
@Entity
@Table(name = "carts")
public class Cart extends BaseEntity {

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CartStatus status;

    @Column(name = "coupon_code", length = 64)
    private String couponCode;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CartItem> items = new ArrayList<>();

    protected Cart() {
    }

    public Cart(Long customerId) {
        this.customerId = customerId;
        this.status = CartStatus.ACTIVE;
    }

    /** Add the product, or increase the quantity if it is already in the cart. */
    public CartItem addOrIncrement(Product product, int quantity, BigDecimal unitPriceSnapshot) {
        return findItem(product.getId())
                .map(item -> {
                    item.setQuantity(item.getQuantity() + quantity);
                    return item;
                })
                .orElseGet(() -> {
                    CartItem item = new CartItem(this, product, quantity, unitPriceSnapshot);
                    items.add(item);
                    return item;
                });
    }

    /** Set the absolute quantity for a product already in the cart. */
    public void setItemQuantity(Long productId, int quantity) {
        CartItem item = findItem(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not in cart"));
        item.setQuantity(quantity);
    }

    /** Remove a product line; no-op if absent. */
    public void removeItem(Long productId) {
        items.removeIf(item -> item.getProduct().getId().equals(productId));
    }

    public Optional<CartItem> findItem(Long productId) {
        return items.stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public void clearCoupon() {
        this.couponCode = null;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public CartStatus getStatus() {
        return status;
    }

    public void setStatus(CartStatus status) {
        this.status = status;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public void setCouponCode(String couponCode) {
        this.couponCode = couponCode;
    }

    public List<CartItem> getItems() {
        return items;
    }
}
