package com.ecomm.oms.order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")
    List<Order> findByCustomerIdOrderByIdDesc(Long customerId);

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<Order> findById(Long id);
}
