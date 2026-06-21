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

    private StockLevel stockFor(InventoryReservation reservation) {
        return stockLevelRepository
                .findByProductIdAndWarehouseId(
                        reservation.getProduct().getId(), reservation.getWarehouse().getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Stock row missing for reservation " + reservation.getId()));
    }
}
