package com.ecomm.oms.inventory;

import com.ecomm.oms.catalog.Product;
import com.ecomm.oms.common.error.InsufficientStockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Concurrency-safe inventory reservation and confirmation — the oversell-prevention
 * centerpiece.
 *
 * <p>{@link #reserve} takes a pessimistic write lock on every stock row for the product
 * (ordered by warehouse priority) before reading availability, so two concurrent checkouts
 * for the last unit serialize on the lock: the first reserves it, the second sees zero
 * available and gets a 409. Allocation is greedy across warehouses and may split a line.
 *
 * <p>Reserve and confirm are deliberately separate: checkout holds stock with {@code reserve},
 * then — only after payment succeeds — calls {@link #confirm} to decrement on-hand. If payment
 * fails the surrounding transaction rolls back and the hold simply never materialises.
 */
@Service
public class InventoryService {

    private final StockLevelRepository stockLevelRepository;
    private final InventoryReservationRepository reservationRepository;

    public InventoryService(StockLevelRepository stockLevelRepository,
                            InventoryReservationRepository reservationRepository) {
        this.stockLevelRepository = stockLevelRepository;
        this.reservationRepository = reservationRepository;
    }

    /**
     * Hold {@code quantity} units of {@code product}, allocating greedily across warehouses by
     * priority under a pessimistic row lock.
     *
     * @throws InsufficientStockException if total available across all warehouses is short
     */
    @Transactional
    public List<InventoryReservation> reserve(Product product, int quantity) {
        List<StockLevel> stockRows = stockLevelRepository.lockByProductForUpdate(product.getId());

        int remaining = quantity;
        List<InventoryReservation> reservations = new ArrayList<>();
        for (StockLevel stock : stockRows) {
            if (remaining == 0) {
                break;
            }
            int take = Math.min(stock.getAvailable(), remaining);
            if (take > 0) {
                stock.reserve(take);
                reservations.add(reservationRepository.save(
                        new InventoryReservation(product, stock.getWarehouse(), take)));
                remaining -= take;
            }
        }

        if (remaining > 0) {
            throw new InsufficientStockException(
                    "Insufficient stock for product " + product.getSku()
                            + ": short by " + remaining + " unit(s)");
        }
        return reservations;
    }

    /** Fulfil holds after successful payment: release reserved units and decrement on-hand. */
    @Transactional
    public void confirm(List<InventoryReservation> reservations) {
        for (InventoryReservation reservation : reservations) {
            stockFor(reservation).confirmReservation(reservation.getQuantity());
            reservation.markConfirmed();
        }
    }

    /** Return held units to availability (used by order cancellation). */
    @Transactional
    public void release(List<InventoryReservation> reservations) {
        for (InventoryReservation reservation : reservations) {
            if (reservation.getStatus() == ReservationStatus.ACTIVE) {
                stockFor(reservation).releaseReservation(reservation.getQuantity());
                reservation.markReleased();
            }
        }
    }

    /**
     * Return an order's stock to availability on cancellation. CONFIRMED reservations (already
     * decremented from on-hand) are added back; still-ACTIVE holds are simply released. Each
     * reservation is moved to RELEASED so a repeated cancel is a no-op.
     */
    @Transactional
    public void restock(List<InventoryReservation> reservations) {
        for (InventoryReservation reservation : reservations) {
            switch (reservation.getStatus()) {
                case CONFIRMED -> {
                    stockFor(reservation).addOnHand(reservation.getQuantity());
                    reservation.markReleased();
                }
                case ACTIVE -> {
                    stockFor(reservation).releaseReservation(reservation.getQuantity());
                    reservation.markReleased();
                }
                case RELEASED -> {
                    // already returned; nothing to do
                }
            }
        }
    }

    /**
     * Add units back to on-hand at a specific warehouse (restock on return). The stock row is
     * created if the product was never stocked there.
     */
    @Transactional
    public void addStock(com.ecomm.oms.catalog.Product product, com.ecomm.oms.inventory.Warehouse warehouse,
                         int quantity) {
        StockLevel stock = stockLevelRepository
                .findByProductIdAndWarehouseId(product.getId(), warehouse.getId())
                .orElseGet(() -> new StockLevel(product, warehouse, 0));
        stock.addOnHand(quantity);
        stockLevelRepository.save(stock);
    }

    private StockLevel stockFor(InventoryReservation reservation) {
        return stockLevelRepository
                .findByProductIdAndWarehouseId(
                        reservation.getProduct().getId(), reservation.getWarehouse().getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Stock row missing for reservation " + reservation.getId()));
    }
}
