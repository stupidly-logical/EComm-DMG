package com.ecomm.oms.inventory;

import com.ecomm.oms.catalog.Product;
import com.ecomm.oms.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Stock of one product at one warehouse. {@code available = onHand − reserved}; reservations
 * hold units between checkout and fulfillment. The {@code @Version} column (from
 * {@link BaseEntity}) plus pessimistic locking during reservation prevent oversell.
 */
@Entity
@Table(name = "stock_levels",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_stock_product_warehouse",
                columnNames = {"product_id", "warehouse_id"}))
public class StockLevel extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand;

    @Column(name = "quantity_reserved", nullable = false)
    private int quantityReserved;

    protected StockLevel() {
    }

    public StockLevel(Product product, Warehouse warehouse, int quantityOnHand) {
        this.product = product;
        this.warehouse = warehouse;
        this.quantityOnHand = quantityOnHand;
        this.quantityReserved = 0;
    }

    /** Units that can still be reserved. */
    public int getAvailable() {
        return quantityOnHand - quantityReserved;
    }

    /** Hold {@code qty} units; caller must ensure {@code qty <= available}. */
    public void reserve(int qty) {
        if (qty > getAvailable()) {
            throw new IllegalArgumentException("Cannot reserve more than available");
        }
        this.quantityReserved += qty;
    }

    /** Confirm a prior reservation as shipped: release the hold and decrement on-hand. */
    public void confirmReservation(int qty) {
        if (qty > quantityReserved) {
            throw new IllegalArgumentException("Cannot confirm more than reserved");
        }
        this.quantityReserved -= qty;
        this.quantityOnHand -= qty;
    }

    /** Release a hold without shipping (cancellation/rollback). */
    public void releaseReservation(int qty) {
        if (qty > quantityReserved) {
            throw new IllegalArgumentException("Cannot release more than reserved");
        }
        this.quantityReserved -= qty;
    }

    /** Add units back to on-hand (receiving / restock on return). */
    public void addOnHand(int qty) {
        this.quantityOnHand += qty;
    }

    public Product getProduct() {
        return product;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public int getQuantityOnHand() {
        return quantityOnHand;
    }

    public void setQuantityOnHand(int quantityOnHand) {
        this.quantityOnHand = quantityOnHand;
    }

    public int getQuantityReserved() {
        return quantityReserved;
    }
}
