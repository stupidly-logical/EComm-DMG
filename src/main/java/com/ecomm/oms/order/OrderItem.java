package com.ecomm.oms.order;

import com.ecomm.oms.catalog.Product;
import com.ecomm.oms.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * A line on a placed order. Product SKU/name and the priced amounts are snapshotted so the
 * order is an immutable historical record independent of later catalog changes.
 * {@code allocatedWarehouseId} is the fulfilling warehouse when the line was satisfied from a
 * single location; null when the quantity was split across warehouses (see reservations).
 */
@Entity
@Table(name = "order_items")
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_sku", nullable = false)
    private String productSku;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "line_discount", nullable = false)
    private BigDecimal lineDiscount;

    @Column(name = "line_tax", nullable = false)
    private BigDecimal lineTax;

    @Column(name = "line_total", nullable = false)
    private BigDecimal lineTotal;

    @Column(name = "allocated_warehouse_id")
    private Long allocatedWarehouseId;

    protected OrderItem() {
    }

    public OrderItem(Order order, Product product, int quantity, BigDecimal unitPrice,
                     BigDecimal lineDiscount, BigDecimal lineTax, BigDecimal lineTotal,
                     Long allocatedWarehouseId) {
        this.order = order;
        this.product = product;
        this.productSku = product.getSku();
        this.productName = product.getName();
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineDiscount = lineDiscount;
        this.lineTax = lineTax;
        this.lineTotal = lineTotal;
        this.allocatedWarehouseId = allocatedWarehouseId;
    }

    public Order getOrder() {
        return order;
    }

    public Product getProduct() {
        return product;
    }

    public String getProductSku() {
        return productSku;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getLineDiscount() {
        return lineDiscount;
    }

    public BigDecimal getLineTax() {
        return lineTax;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public Long getAllocatedWarehouseId() {
        return allocatedWarehouseId;
    }
}
