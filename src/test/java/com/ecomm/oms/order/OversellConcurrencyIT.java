package com.ecomm.oms.order;

import com.ecomm.oms.cart.CartService;
import com.ecomm.oms.cart.dto.AddCartItemRequest;
import com.ecomm.oms.catalog.Product;
import com.ecomm.oms.catalog.ProductRepository;
import com.ecomm.oms.common.error.InsufficientStockException;
import com.ecomm.oms.inventory.StockLevel;
import com.ecomm.oms.inventory.StockLevelRepository;
import com.ecomm.oms.inventory.Warehouse;
import com.ecomm.oms.inventory.WarehouseRepository;
import com.ecomm.oms.order.dto.CheckoutRequest;
import com.ecomm.oms.support.IntegrationTestSupport;
import com.ecomm.oms.user.Role;
import com.ecomm.oms.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The oversell-prevention proof. {@code STOCK_UNITS} units are spread across two warehouses;
 * {@code SHOPPERS} customers each try to buy one unit at the same instant. Pessimistic row
 * locking in {@link com.ecomm.oms.inventory.InventoryService#reserve} must let exactly
 * {@code STOCK_UNITS} checkouts succeed, reject the rest with 409, and leave stock at zero
 * with no negative or dangling quantities.
 */
class OversellConcurrencyIT extends IntegrationTestSupport {

    private static final int STOCK_UNITS = 3;   // 2 in warehouse A + 1 in warehouse B
    private static final int SHOPPERS = 12;

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private WarehouseRepository warehouseRepository;
    @Autowired
    private StockLevelRepository stockLevelRepository;
    @Autowired
    private CartService cartService;
    @Autowired
    private CheckoutService checkoutService;
    @Autowired
    private OrderRepository orderRepository;

    @Test
    void parallelCheckoutsNeverOversell() throws Exception {
        Product product = productRepository.save(new Product(
                "RACE-" + System.nanoTime(), "Hot Item", null,
                new BigDecimal("25.00"), "NONE-" + System.nanoTime(), true, null));
        Warehouse whA = warehouseRepository.save(new Warehouse("A-" + System.nanoTime(), "A", "US", 1));
        Warehouse whB = warehouseRepository.save(new Warehouse("B-" + System.nanoTime(), "B", "US", 2));
        stockLevelRepository.save(new StockLevel(product, whA, 2));
        stockLevelRepository.save(new StockLevel(product, whB, 1));

        // Each shopper has their own cart holding one unit.
        List<Long> customerIds = new ArrayList<>();
        for (int i = 0; i < SHOPPERS; i++) {
            User customer = createUser(Role.CUSTOMER);
            cartService.addItem(customer.getId(), new AddCartItemRequest(product.getId(), 1));
            customerIds.add(customer.getId());
        }

        // Fire all checkouts as simultaneously as possible.
        ExecutorService pool = Executors.newFixedThreadPool(SHOPPERS);
        CountDownLatch ready = new CountDownLatch(SHOPPERS);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (Long customerId : customerIds) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    checkoutService.placeOrder(customerId, new CheckoutRequest(null, null, null));
                    succeeded.incrementAndGet();
                } catch (InsufficientStockException e) {
                    soldOut.incrementAndGet();
                } catch (Exception e) {
                    other.incrementAndGet();
                }
                return null;
            }));
        }

        ready.await();      // all threads parked at the gate
        go.countDown();     // release them together
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        // Exactly the available units sold; everyone else got a clean sold-out.
        assertThat(succeeded.get()).isEqualTo(STOCK_UNITS);
        assertThat(soldOut.get()).isEqualTo(SHOPPERS - STOCK_UNITS);
        assertThat(other.get()).as("no unexpected errors").isZero();

        // Stock fully drained, nothing negative, no dangling reservations.
        StockLevel a = stockLevelRepository.findByProductIdAndWarehouseId(product.getId(), whA.getId()).orElseThrow();
        StockLevel b = stockLevelRepository.findByProductIdAndWarehouseId(product.getId(), whB.getId()).orElseThrow();
        assertThat(a.getQuantityOnHand()).isZero();
        assertThat(b.getQuantityOnHand()).isZero();
        assertThat(a.getQuantityReserved()).isZero();
        assertThat(b.getQuantityReserved()).isZero();

        // Exactly STOCK_UNITS orders were placed.
        long placed = customerIds.stream()
                .mapToLong(id -> orderRepository.findByCustomerIdOrderByIdDesc(id).size())
                .sum();
        assertThat(placed).isEqualTo(STOCK_UNITS);
    }
}
