package com.ecomm.oms.cart;

import com.ecomm.oms.catalog.Product;
import com.ecomm.oms.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/**
 * A line in a cart. {@code unitPriceSnapshot} captures the product's price when added so a
 * later catalog price change does not silently alter the customer's cart.
 */
@Entity
@Table(name = "cart_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_cart_items_cart_product",
                columnNames = {"cart_id", "product_id"}))
public class CartItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_snapshot", nullable = false)
    private BigDecimal unitPriceSnapshot;

    protected CartItem() {
    }

    public CartItem(Cart cart, Product product, int quantity, BigDecimal unitPriceSnapshot) {
        this.cart = cart;
        this.product = product;
        this.quantity = quantity;
        this.unitPriceSnapshot = unitPriceSnapshot;
    }

    public Cart getCart() {
        return cart;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPriceSnapshot() {
        return unitPriceSnapshot;
    }

    public void setUnitPriceSnapshot(BigDecimal unitPriceSnapshot) {
        this.unitPriceSnapshot = unitPriceSnapshot;
    }
}
