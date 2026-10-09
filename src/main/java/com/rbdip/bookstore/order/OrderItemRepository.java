package com.rbdip.bookstore.order;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    // Product is needed after the repository transaction with open-in-view disabled.
    // One query per order remains the subject of Lab 4.
    @EntityGraph(attributePaths = "product")
    List<OrderItem> findByOrderId(Long orderId);
}
