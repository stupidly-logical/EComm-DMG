package com.ecomm.oms.returns;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<ReturnRequest> findById(Long id);

    List<ReturnRequest> findByOrderIdOrderByIdAsc(Long orderId);
}
