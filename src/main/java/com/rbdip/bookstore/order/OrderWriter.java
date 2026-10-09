package com.rbdip.bookstore.order;

import com.rbdip.bookstore.customer.Customer;
import com.rbdip.bookstore.customer.CustomerService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OrderWriter {

    private static final String INITIAL_STATUS = "new";
    private final CustomerService customerService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public OrderWriter(CustomerService customerService,
            OrderRepository orderRepository, OrderItemRepository orderItemRepository) {
        this.customerService = customerService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    public Order save(CreateOrderRequest request, List<ResolvedOrderItem> items) {
        Customer customer = customerService.findOrCreate(
                request.customerFullName(), request.customerAddress(), request.customerPhone());
        Order order = orderRepository.save(new Order(customer, INITIAL_STATUS));
        for (ResolvedOrderItem item : items) {
            orderItemRepository.save(item.toOrderItem(order.getId()));
        }
        return order;
    }
}
