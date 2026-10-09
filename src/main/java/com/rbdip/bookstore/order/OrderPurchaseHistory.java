package com.rbdip.bookstore.order;

import com.rbdip.bookstore.review.PurchaseHistory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderPurchaseHistory implements PurchaseHistory {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public OrderPurchaseHistory(OrderRepository orderRepository, OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPurchases() {
        return orderRepository.count() > 0 && orderItemRepository.count() > 0;
    }
}
